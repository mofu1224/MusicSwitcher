package dev.musicswitcher

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.util.UUID

internal data class Track(val uri: Uri, val title: String, val directory: String)

internal data class FolderContents(
    val name: String,
    val tracks: List<Track>,
    val canSave: Boolean,
    val skippedFolders: Int,
)

internal class FolderLibrary(private val resolver: ContentResolver) {
    private data class Entry(val id: String, val name: String, val mime: String, val flags: Int)

    suspend fun read(tree: Uri): FolderContents {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val root = document(tree, rootId)
        val folder = query(root) { cursor ->
            if (!cursor.moveToFirst()) throw IOException("Folder unavailable")
            cursor.entry()
        }
        val tracks = mutableListOf<Track>()
        val visited = mutableSetOf<String>()
        val pending = ArrayDeque<Pair<String, String>>().apply { add(rootId to "") }
        var skipped = 0
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (id, directory) = pending.removeFirst()
            if (!visited.add(id)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            try {
                query(children) { cursor ->
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val entry = cursor.entry()
                        when {
                            entry.mime == Document.MIME_TYPE_DIR ->
                                pending.add(entry.id to listOfNotNull(directory.takeIf { it.isNotEmpty() }, entry.name).joinToString("/"))
                            entry.flags and Document.FLAG_VIRTUAL_DOCUMENT != 0 -> Unit
                            isAudioFile(entry.name, entry.mime) ->
                                tracks.add(Track(document(tree, entry.id), entry.name, directory))
                        }
                    }
                }
            } catch (error: SecurityException) {
                if (id == rootId) throw error
                skipped++
            } catch (error: IOException) {
                if (id == rootId) throw error
                skipped++
            }
        }
        val writeGrant = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        return FolderContents(
            name = folder.name,
            tracks = tracks.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { track: Track -> track.directory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
            ),
            canSave = writeGrant && folder.flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0,
            skippedFolders = skipped,
        )
    }

    suspend fun publish(tree: Uri, file: File): String {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val root = document(tree, rootId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val existing = query(childrenUri) { cursor ->
            buildSet { while (cursor.moveToNext()) cursor.getString(1)?.let { add(it.lowercase()) } }
        }
        val finalName = uniqueAudioName(file.name, existing)
        val stage = DocumentsContract.createDocument(
            resolver, root, "application/octet-stream", "MusicSwitcher-${UUID.randomUUID()}.partial",
        ) ?: throw IOException("Could not stage download")
        var published = false
        try {
            val canRename = query(stage) { cursor ->
                cursor.moveToFirst() && cursor.getInt(3) and Document.FLAG_SUPPORTS_RENAME != 0
            }
            if (!canRename) throw IOException("Folder does not support finalizing files")
            val output = resolver.openOutputStream(stage, "w") ?: throw IOException("Could not open destination")
            output.use { destination ->
                file.inputStream().use { source ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            val saved = DocumentsContract.renameDocument(resolver, stage, finalName)
                ?: throw IOException("Could not finalize download")
            published = true
            return runCatching {
                query(saved) { cursor -> if (cursor.moveToFirst()) cursor.getString(1) ?: finalName else finalName }
            }.getOrDefault(finalName)
        } finally {
            if (!published) runCatching { DocumentsContract.deleteDocument(resolver, stage) }
        }
    }

    private inline fun <T> query(uri: Uri, read: (Cursor) -> T): T =
        resolver.query(uri, columns, null, null, null)?.use(read) ?: throw IOException("Document provider unavailable")

    private fun document(tree: Uri, id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun Cursor.entry() = Entry(getString(0), getString(1) ?: getString(0), getString(2).orEmpty(), getInt(3))

    private companion object {
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS)
    }
}
