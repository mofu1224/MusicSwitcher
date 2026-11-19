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

internal data class MusicUiState(
    val folder: FolderContents? = null,
    val scanning: Boolean = false,
    val folderError: String? = null,
    val darkMode: Boolean? = null,
    val download: DownloadUi = DownloadUi.Idle,
)

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
        MusicUiState(darkMode = if (preferences.contains("dark")) preferences.getBoolean("dark", false) else null),
    )
    val state = ui.asStateFlow()

    init { refresh() }

    fun selectFolder(uri: Uri, resultFlags: Int, onChanged: () -> Unit) {
        if (ui.value.download is DownloadUi.Working) return
        val flags = resultFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            resolver.takePersistableUriPermission(uri, flags)
            if (resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission }) {
                throw SecurityException("Folder read grant missing")
            }
        } catch (_: SecurityException) {
            ui.update { it.copy(folderError = "フォルダへのアクセスを保存できませんでした。別のフォルダを選んでください。") }
            return
        }
        val previous = tree
        tree = uri
        preferences.edit { putString("tree", uri.toString()) }
        if (uri != previous) {
            onChanged()
            ui.update { it.copy(folder = null, download = DownloadUi.Idle) }
            if (previous != null) releaseFolder(previous)
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
                ui.update { it.copy(folder = contents, scanning = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ui.update {
                    it.copy(folder = null, scanning = false, folderError = "フォルダを読み込めません。フォルダを選び直してください。")
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
                ui.update { it.copy(download = DownloadUi.Message("保存しました：$name")) }
                refresh()
            } catch (_: TimeoutCancellationException) {
                ui.update { it.copy(download = DownloadUi.Message("取得がタイムアウトしました。通信状態を確認してください。")) }
            } catch (error: CancellationException) {
                ui.update { it.copy(download = DownloadUi.Message("ダウンロードをキャンセルしました。")) }
                throw error
            } catch (error: Exception) {
                Log.w("MusicSwitcher", "Audio import failed (${error.javaClass.simpleName}); saving=$saving")
                val message = if (saving) "保存できませんでした。フォルダの権限と空き容量を確認してください。"
                else "音声を取得できませんでした。URL・対応サイト・通信状態を確認してください。"
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

    private fun releaseFolder(uri: Uri) {
        val grant = resolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        var flags = 0
        if (grant.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (grant.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.releasePersistableUriPermission(uri, flags) }
    }
}
