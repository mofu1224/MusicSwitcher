package dev.musicswitcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController

@Composable
internal fun MusicScreen(
    state: MusicUiState,
    playing: NowPlaying,
    controller: MediaController?,
    connectionFailed: Boolean,
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    onChooseFolder: () -> Unit,
    onRefresh: () -> Unit,
    onDownload: (String) -> Boolean,
    onCancelDownload: () -> Unit,
    onDismissDownload: () -> Unit,
) {
    var showDownload by rememberSaveable { mutableStateOf(false) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val folder = state.folder
    val tracks = folder?.tracks.orEmpty()
    val downloading = state.download is DownloadUi.Working
    val scroll = rememberLazyListState()
    val artwork = rememberTrackArtwork(playing.artworkData)
    val playerOpen = showPlayer && playing.id != null
    val playingLocation = remember(playing.artist, playing.id, folder) {
        playing.artist ?: tracks.firstOrNull { it.uri.toString() == playing.id }
            ?.directory?.ifBlank { folder?.name.orEmpty() }?.takeIf { it.isNotBlank() } ?: "端末の音楽"
    }
    BackHandler(enabled = playerOpen) { showPlayer = false }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            if (playerOpen) {
                NowPlayingScreen(playing, playingLocation, artwork, controller, onClose = { showPlayer = false })
            } else {
                Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "MusicSwitcher", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = onToggleTheme) {
                            MusicGlyph(
                                if (darkMode) Glyph.Sun else Glyph.Moon, Modifier.size(22.dp),
                                if (darkMode) "ライトに切替" else "ダークに切替",
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(), state = scroll,
                        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                    ) {
                        item(key = "header") {
                            Text(
                                "ライブラリ", Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp).semantics { heading() },
                                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            )
                            LibraryFolderCard(folder)
                            LibraryActions(
                                canPick = !downloading,
                                canSave = folder?.canSave == true && !downloading && !state.scanning,
                                canRefresh = folder != null && !state.scanning,
                                onChooseFolder = onChooseFolder,
                                onSave = { showDownload = true },
                                onRefresh = onRefresh,
                            )
                            if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                            state.folderError?.let { InlineMessage(it) }
                            if (connectionFailed) InlineMessage("再生サービスに接続できません。アプリを開き直してください。")
                            if (folder?.canSave == false) InlineMessage("読み取り専用のフォルダです。保存するには別のフォルダを選んでください。")
                            if (folder != null && folder.skippedFolders > 0) InlineMessage("読み取れなかったサブフォルダ：${folder.skippedFolders}件")
                            DownloadPanel(state.download, onCancelDownload, onDismissDownload)
                            Text(
                                "曲", Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp).semantics { heading() },
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            )
                        }
                        if (tracks.isEmpty()) {
                            item(key = "empty") {
                                InlineMessage(
                                    when {
                                        state.scanning -> "フォルダを読み込んでいます…"
                                        folder == null -> "フォルダを選ぶと、ここに曲が表示されます。"
                                        folder.canSave -> "まだ曲がありません。「URLから保存」で音楽を追加できます。"
                                        else -> "このフォルダに再生できる音声ファイルはありません。"
                                    },
                                )
                            }
                        } else {
                            itemsIndexed(tracks, key = { _, track -> track.uri.toString() }) { index, track ->
                                val active = playing.id == track.uri.toString()
                                TrackRow(
                                    track, folder?.name.orEmpty(), active, if (active) artwork else null,
                                    enabled = controller != null,
                                    onClick = { controller?.playTracks(tracks, index) },
                                )
                            }
                        }
                    }
                    if (playing.id != null) {
                        MiniPlayer(playing, playingLocation, artwork, controller, onExpand = { showPlayer = true })
                    }
                }
            }
        }
    }
    if (showDownload) {
        DownloadDialog(onDismiss = { showDownload = false }, onDownload = {
            if (onDownload(it)) {
                showDownload = false
                true
            } else false
        })
    }
}

@Composable
private fun LibraryFolderCard(folder: FolderContents?) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MusicArtwork(folder?.name.orEmpty(), Modifier.size(68.dp), folder = true)
            Column(Modifier.weight(1f)) {
                Text("音楽フォルダ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    folder?.name ?: "フォルダを選択", maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    folder?.let { "${it.tracks.size} 曲" } ?: "この端末にある音楽を再生",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LibraryActions(
    canPick: Boolean,
    canSave: Boolean,
    canRefresh: Boolean,
    onChooseFolder: () -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onChooseFolder, enabled = canPick, modifier = Modifier.weight(1f, fill = false)) {
            Text("フォルダを選ぶ")
        }
        TextButton(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1f, fill = false)) {
            Text("URLから保存")
        }
        IconButton(onClick = onRefresh, enabled = canRefresh) {
            MusicGlyph(Glyph.Refresh, Modifier.size(22.dp), "更新")
        }
    }
}

@Composable
private fun TrackRow(track: Track, folderName: String, active: Boolean, art: ImageBitmap?, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(9.dp),
        color = if (active) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { selected = active }
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicArtwork(track.title, Modifier.size(48.dp), art)
            Column(Modifier.weight(1f)) {
                Text(
                    track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    track.directory.ifBlank { folderName }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (active) {
                MusicGlyph(Glyph.Play, Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun InlineMessage(message: String) {
    Text(
        message, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
    )
}
