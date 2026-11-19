package dev.musicswitcher

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files

internal data class SavedLibrary(val uri: Uri, val name: String)

internal data class MusicUiState(
    val libraries: List<SavedLibrary> = emptyList(),
    val activeLibrary: Uri? = null,
    val folder: FolderContents? = null,
    val hasFolderSelection: Boolean = false,
    val scanning: Boolean = false,
    val folderError: String? = null,
    val darkMode: Boolean? = null,
    val download: DownloadUi = DownloadUi.Idle,
) {
    val canRefresh get() = hasFolderSelection && !scanning
}

internal sealed interface DownloadUi {
    data object Idle : DownloadUi
    data class Working(val id: Long, val progress: Int? = null, val saving: Boolean = false) : DownloadUi
    data class Message(val text: String) : DownloadUi
}

internal class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val resolver = application.contentResolver
    private val preferences = application.getSharedPreferences("music", Application.MODE_PRIVATE)
    private val library = FolderLibrary(resolver)
    private val downloader = AudioDownloader(application)
    private val cacheDir = application.cacheDir
    private var tree = preferences.getString("tree", null)?.let(Uri::parse)
    private var scanJob: Job? = null
    private var downloadJob: Job? = null
    private var downloadId = 0L
    private val ui = MutableStateFlow(
        MusicUiState(
            hasFolderSelection = tree != null,
            libraries = savedLibraries(), activeLibrary = tree,
            darkMode = if (preferences.contains("dark")) preferences.getBoolean("dark", false) else null,
        ),
    )
    val state = ui.asStateFlow()

    init { refresh() }

    private fun savedLibraries(): List<SavedLibrary> {
        val ids = preferences.getStringSet("libraries", emptySet()).orEmpty() + listOfNotNull(tree?.toString())
        val entries = ids.map { id ->
            val uri = Uri.parse(id)
            SavedLibrary(uri, preferences.getString("name:$id", null)
                ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.ifBlank { null }
                ?: "Folder")
        }.sortedWith(compareBy<SavedLibrary> { it.name.lowercase() }.thenBy { it.uri.toString() })
        return ordered("libraryOrder", entries) { it.uri.toString() }
    }

    private fun <T> ordered(key: String, items: List<T>, id: (T) -> String): List<T> {
        val stored = preferences.getString(key, null) ?: return items
        val ranks = stored.split('\n').withIndex().associate { it.value to it.index }
        return items.sortedBy { ranks[id(it)] ?: Int.MAX_VALUE }
    }

    private fun <T> move(key: String, items: List<T>, from: String, to: String, id: (T) -> String): List<T> {
        val a = items.indexOfFirst { id(it) == from }
        val b = items.indexOfFirst { id(it) == to }
        if (a < 0 || b < 0 || a == b) return items
        return items.toMutableList().apply {
            add(b, removeAt(a))
            preferences.edit { putString(key, joinToString("\n", transform = id)) }
        }
    }

    fun moveLibrary(from: String, to: String) {
        val entries = move("libraryOrder", ui.value.libraries, from, to) { it.uri.toString() }
        ui.update { it.copy(libraries = entries) }
    }

    fun moveTrack(from: String, to: String) {
        val folder = ui.value.folder ?: return
        val tracks = move("trackOrder:$tree", folder.tracks, from, to) { it.uri.toString() }
        ui.update { it.copy(folder = folder.copy(tracks = tracks)) }
    }

    fun deleteTrack(uri: Uri, onDeleted: () -> Unit) {
        val selected = tree ?: return
        if (ui.value.folder?.tracks?.none { it.uri == uri } != false) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { library.deleteTrack(uri) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (tree == selected) ui.update { it.copy(folderError = "Could not delete the file. Check folder access.") }
                return@launch
            }
            onDeleted()
            if (tree == selected) refresh()
        }
    }

    fun forgetLibrary(uri: Uri, onChanged: () -> Unit) {
        val entries = ui.value.libraries
        if (ui.value.download is DownloadUi.Working || entries.none { it.uri == uri }) return
        val remaining = entries.filterNot { it.uri == uri }
        val changed = tree == uri
        if (changed) {
            scanJob?.cancel()
            tree = remaining.firstOrNull()?.uri
            onChanged()
        }
        preferences.edit {
            putString("tree", tree?.toString())
            putStringSet("libraries", remaining.map { it.uri.toString() }.toSet())
            putString("libraryOrder", remaining.joinToString("\n") { it.uri.toString() })
            remove("name:$uri")
            remove("trackOrder:$uri")
        }
        resolver.persistedUriPermissions.firstOrNull { it.uri == uri }?.let { grant ->
            val flags = (if (grant.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (grant.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            try { resolver.releasePersistableUriPermission(uri, flags) } catch (_: SecurityException) { }
        }
        ui.update {
            if (!changed) it.copy(libraries = remaining)
            else it.copy(libraries = remaining, activeLibrary = tree, hasFolderSelection = tree != null,
                folder = null, scanning = false, folderError = null, download = DownloadUi.Idle)
        }
        if (changed) refresh()
    }

    fun selectFolder(uri: Uri, resultFlags: Int, onChanged: () -> Unit) {
        if (ui.value.download is DownloadUi.Working) return
        val flags = resultFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            resolver.takePersistableUriPermission(uri, flags)
            if (resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission }) {
                throw SecurityException("Folder read grant missing")
            }
        } catch (_: SecurityException) {
            ui.update { it.copy(folderError = "Access denied. Choose another folder.") }
            return
        }
        activate(uri, onChanged)
    }

    fun switchLibrary(uri: Uri, onChanged: () -> Unit) {
        if (uri == tree || ui.value.download is DownloadUi.Working || ui.value.libraries.none { it.uri == uri }) return
        activate(uri, onChanged)
    }

    private fun activate(uri: Uri, onChanged: () -> Unit) {
        val previous = tree
        val ids = ui.value.libraries.mapTo(mutableSetOf(uri.toString())) { it.uri.toString() }
        tree = uri
        preferences.edit { putString("tree", uri.toString()); putStringSet("libraries", ids) }
        if (uri != previous) {
            onChanged()
            ui.update { it.copy(folder = null, hasFolderSelection = true, download = DownloadUi.Idle,
                libraries = savedLibraries(), activeLibrary = uri) }
        }
        refresh()
    }

    fun refresh() {
        val selected = tree ?: return
        scanJob?.cancel()
        ui.update { it.copy(scanning = true, folderError = null) }
        scanJob = viewModelScope.launch {
            try {
                val contents = withContext(Dispatchers.IO) { library.read(selected) }
                if (tree != selected) return@launch
                preferences.edit { putString("name:$selected", contents.name) }
                val tracks = ordered("trackOrder:$selected", contents.tracks) { it.uri.toString() }
                ui.update { it.copy(folder = contents.copy(tracks = tracks), libraries = savedLibraries(), scanning = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (tree != selected) return@launch
                ui.update {
                    it.copy(folder = null, scanning = false, folderError = "Folder unavailable. Refresh or choose another.")
                }
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        preferences.edit { putBoolean("dark", enabled) }
        ui.update { it.copy(darkMode = enabled) }
    }

    fun download(input: String): Boolean {
        val url = parseDownloadUrl(input) ?: return false
        val selected = tree ?: return false
        if (ui.value.folder?.canSave != true || downloadJob?.isCompleted == false) return false
        val id = ++downloadId
        ui.update { it.copy(download = DownloadUi.Working(id)) }
        downloadJob = viewModelScope.launch {
            var workspace: File? = null
            var saving = false
            try {
                val directory = withContext(Dispatchers.IO) {
                    Files.createTempDirectory(cacheDir.toPath(), "audio-").toFile().also { workspace = it }
                }
                val audio = withTimeout(20 * 60 * 1_000L) {
                    downloader.fetch(url, directory) { progress ->
                        ui.update { state ->
                            val working = state.download as? DownloadUi.Working
                            if (working?.id != id || working.saving || working.progress == progress) state
                            else state.copy(download = working.copy(progress = progress))
                        }
                    }
                }
                saving = true
                ui.update { it.copy(download = DownloadUi.Working(id, saving = true)) }
                val name = withContext(Dispatchers.IO) { library.publish(selected, audio) }
                ui.update { it.copy(download = DownloadUi.Message("Saved: $name")) }
                refresh()
            } catch (_: TimeoutCancellationException) {
                ui.update { it.copy(download = DownloadUi.Message("Timed out. Retry.")) }
            } catch (error: CancellationException) {
                ui.update { it.copy(download = DownloadUi.Message("Canceled")) }
                throw error
            } catch (error: Exception) {
                Log.w("MusicSwitcher", "Audio import failed (${error.javaClass.simpleName}); saving=$saving")
                val message = if (saving) "Save failed. Check access and storage."
                else "Fetch failed. Check link and connection."
                ui.update { it.copy(download = DownloadUi.Message(message)) }
            } finally {
                withContext(Dispatchers.IO + NonCancellable) { workspace?.deleteRecursively() }
            }
        }
        return true
    }

    fun cancelDownload() {
        if ((ui.value.download as? DownloadUi.Working)?.saving == false) downloadJob?.cancel()
    }

    fun dismissDownload() {
        ui.update { if (it.download is DownloadUi.Message) it.copy(download = DownloadUi.Idle) else it }
    }

}
