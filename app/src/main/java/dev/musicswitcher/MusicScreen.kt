package dev.musicswitcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController

@Composable
internal fun MusicScreen(
    state: MusicUiState, playing: NowPlaying, controller: MediaController?, connectionFailed: Boolean,
    darkMode: Boolean, onToggleTheme: () -> Unit, onChooseFolder: () -> Unit, onRefresh: () -> Unit,
    onDownload: (String) -> Boolean, onCancelDownload: (Long) -> Unit, onDismissDownload: (Long) -> Unit,
    onSwitchLibrary: (android.net.Uri) -> Unit,
    onDeleteTrack: (android.net.Uri) -> Unit, onForgetLibrary: (android.net.Uri) -> Unit,
    onMoveLibrary: (String, String) -> Unit, onMoveTrack: (String, String) -> Unit,
) {
    var showDownload by rememberSaveable { mutableStateOf(false) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val folder = state.folder
    val tracks = folder?.tracks.orEmpty()
    var trackAction by remember(state.activeLibrary) { mutableStateOf<String?>(null) }
    var libraryAction by remember { mutableStateOf<String?>(null) }
    val downloading = state.downloading
    val scroll = key(state.activeLibrary) { rememberLazyListState() }
    val trackDrag = rememberListDrag(scroll)
    val artwork = rememberTrackArtwork(playing.artworkData)
    val playerOpen = showPlayer && playing.id != null
    val location = remember(playing.artist, playing.id, folder) {
        playing.artist ?: tracks.firstOrNull { it.uri.toString() == playing.id }
            ?.directory?.ifBlank { folder?.name.orEmpty() }?.takeIf(String::isNotBlank) ?: "Local music"
    }
    BackHandler(playerOpen) { showPlayer = false }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (playerOpen) {
                    IconButton(onClick = { showPlayer = false }) {
                        MusicGlyph(Glyph.Collapse, Modifier.size(24.dp), "Back to library")
                    }
                }
                if (!playerOpen && folder == null && state.canRefresh) {
                    IconButton(onClick = onRefresh) { MusicGlyph(Glyph.Refresh, Modifier.size(18.dp), "Refresh") }
                }
                if (!playerOpen && state.hasFolderSelection) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !downloading) {
                            MusicGlyph(Glyph.Folder, Modifier.size(22.dp), "Libraries")
                        }
                        if (menu) androidx.compose.ui.window.Dialog(onDismissRequest = { menu = false }) {
                            Surface(shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(vertical = 8.dp)) {
                            val libraryList = rememberLazyListState()
                            val libraryDrag = rememberListDrag(libraryList)
                             LazyColumn(Modifier.weight(1f, fill = false).dragList(libraryDrag, onMoveLibrary) { libraryAction = it }, state = libraryList) {
                            itemsIndexed(state.libraries, key = { _, entry -> entry.uri.toString() }) { i, entry ->
                                val active = entry.uri == state.activeLibrary
                                DropdownMenuItem(text = {
                                    Column {
                                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (state.libraries.count { it.name == entry.name } > 1)
                                            Text(entry.uri.lastPathSegment ?: entry.uri.toString(),
                                                style = MaterialTheme.typography.bodySmall)
                                    }
                                }, modifier = Modifier.dragRow(entry.uri.toString(),
                                    state.libraries.getOrNull(i-1)?.uri?.toString(), state.libraries.getOrNull(i+1)?.uri?.toString(),
                                     libraryDrag, onMoveLibrary, "Remove library") { libraryAction = entry.uri.toString() }.semantics { selected = active },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (active) SwitchRails(false, Modifier.size(20.dp))
                                            if (state.libraries.size > 1) ReorderHandle(libraryDrag, entry.uri.toString())
                                        }
                                    },
                                    onClick = { menu = false; onSwitchLibrary(entry.uri) })
                            }
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Add library") }, onClick = { menu = false; onChooseFolder() })
                            }
                            }
                        }
                    }
                    IconButton(onClick = { showDownload = true }, enabled = folder?.canSave == true) {
                        MusicGlyph(Glyph.Download, Modifier.size(22.dp), "Import link")
                    }
                }
                IconButton(onClick = onToggleTheme) {
                    MusicGlyph(if (darkMode) Glyph.Sun else Glyph.Moon, Modifier.size(20.dp),
                        if (darkMode) "Light mode" else "Dark mode")
                }
            }
            AnimatedContent(playerOpen, transitionSpec = {
                val direction = if (targetState) 1 else -1
                (slideInVertically(tween(290, easing = FastOutSlowInEasing)) { direction * it / 12 } +
                    fadeIn(tween(170))) togetherWith fadeOut(tween(85))
            }, label = "player reveal") { expanded ->
            if (expanded) NowPlayingScreen(playing, location, artwork, controller)
            else Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                if (state.downloads.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 156.dp)) {
                    items(state.downloads, key = { it.id }) { item ->
                        DownloadPanel(item, { onCancelDownload(item.id) }, { onDismissDownload(item.id) })
                    }
                }
                if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth().weight(1f).dragList(trackDrag, onMoveTrack) { trackAction = it }, state = scroll,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    item {
                        state.folderError?.let { InlineMessage(it) }
                        if (connectionFailed) InlineMessage("Playback unavailable. Reopen the app.")
                        if (folder?.canSave == false) InlineMessage("Read-only folder")
                        if ((folder?.skippedFolders ?: 0) > 0) InlineMessage("Folders skipped: ${folder?.skippedFolders}")
                    }
                    if (folder == null) item {
                        Column(Modifier.fillParentMaxHeight(.78f).fillMaxWidth(),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            SwitchRails(false, Modifier.width(168.dp).height(90.dp))
                            Spacer(Modifier.height(36.dp))
                            TextButton(onClick = onChooseFolder, enabled = !downloading) {
                                MusicGlyph(Glyph.Folder, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Choose folder")
                            }
                        }
                    } else {
                        item {
                            Column(Modifier.padding(top = 30.dp)) {
                                Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.headlineLarge)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                                    TextButton(onClick = { controller?.playTracks(tracks.shuffled(), 0) },
                                        enabled = tracks.isNotEmpty() && controller != null) { Text("Shuffle") }
                                    IconButton(onClick = onRefresh, enabled = state.canRefresh) {
                                        MusicGlyph(Glyph.Refresh, Modifier.size(18.dp), "Refresh")
                                    }
                                }
                                HorizontalDivider()
                            }
                             if (tracks.isEmpty() && !state.scanning) {
                                 InlineMessage("No tracks")
                                 Text("Add audio files to this folder, then tap Refresh.",
                                     style = MaterialTheme.typography.bodySmall)
                                 if (folder.canSave) TextButton(onClick = { showDownload = true }) {
                                     Text("Import link")
                                 }
                             }
                        }
                        itemsIndexed(tracks, key = { _, track -> track.uri.toString() }) { index, track ->
                            val active = playing.id == track.uri.toString()
                            Row(Modifier.fillMaxWidth().dragRow(track.uri.toString(), tracks.getOrNull(index-1)?.uri?.toString(),
                                 tracks.getOrNull(index+1)?.uri?.toString(), trackDrag, onMoveTrack, "Delete file") { trackAction = track.uri.toString() }
                                .clickable(enabled = controller != null, role = Role.Button) {
                                controller?.playTracks(tracks, index)
                            }.semantics { selected = active }.heightIn(min = 56.dp).padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(44.dp)) {
                                     if (active) SwitchRails(playing.isPlaying, Modifier.size(26.dp))
                                    else Text((index + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                                    if (track.directory.isNotBlank()) Text(track.directory, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (tracks.size > 1) ReorderHandle(trackDrag, track.uri.toString())
                            }
                        }
                    }
                }
                if (playing.id != null) MiniPlayer(playing, location, artwork, controller) { showPlayer = true }
            }
            }
        }
    }
    if (showDownload) DownloadDialog({ showDownload = false }) { url ->
        onDownload(url).also { if (it) showDownload = false }
    }
    tracks.firstOrNull { it.uri.toString() == trackAction }?.let { track ->
        RemovalDialog(track.title, "Permanently delete this audio file?",
            "Delete file", { trackAction = null }) { onDeleteTrack(track.uri) }
    }
    state.libraries.firstOrNull { it.uri.toString() == libraryAction }?.let { entry ->
        RemovalDialog(entry.name, "Remove this library? The folder and its files will be kept.",
            "Remove library", { libraryAction = null }) { onForgetLibrary(entry.uri) }
    }
}

@Composable
private fun RemovalDialog(name: String, message: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(name) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { dismiss(); confirm() }) { Text(action) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
internal fun InlineMessage(message: String) {
    Text(message, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
