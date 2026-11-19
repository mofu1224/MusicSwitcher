package dev.musicswitcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import java.util.Locale

@Composable
internal fun MiniPlayer(
    playing: NowPlaying, location: String, artwork: ImageBitmap?, controller: MediaController?, onExpand: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        shape = RoundedCornerShape(12.dp), color = scheme.surfaceContainerHighest,
    ) {
        Column {
            Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f)
                        .clickable(role = Role.Button, onClick = onExpand)
                        .semantics { contentDescription = "再生画面を開く" }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MusicArtwork(playing.title, Modifier.size(44.dp), artwork)
                    Column(Modifier.weight(1f)) {
                        Text(
                            playing.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            playbackCaption(playing, location), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { controller?.togglePlayback() }, enabled = controller != null) {
                    PlayPauseGlyph(playing, Modifier.size(24.dp))
                }
                if (playing.canNext) {
                    IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = controller != null) {
                        MusicGlyph(Glyph.Next, Modifier.size(21.dp), "次へ")
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(scheme.outlineVariant)) {
                Box(Modifier.fillMaxWidth(playing.fraction).height(2.dp).background(scheme.primary))
            }
        }
    }
}

@Composable
internal fun NowPlayingScreen(
    playing: NowPlaying, location: String, artwork: ImageBitmap?, controller: MediaController?, onClose: () -> Unit,
) {
    Column(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(top = 4.dp, start = 12.dp, end = 12.dp)) {
            Text(
                "再生中", Modifier.align(Alignment.Center).semantics { heading() },
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                MusicGlyph(Glyph.Collapse, Modifier.size(24.dp), "ライブラリへ戻る")
            }
        }
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center,
        ) {
            val sideBySide = maxWidth >= 560.dp && maxHeight < 520.dp
            if (sideBySide) {
                val artworkSize = (maxHeight - 24.dp).coerceIn(100.dp, 360.dp)
                Row(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    MusicArtwork(playing.title, Modifier.size(artworkSize), artwork)
                    PlaybackDetails(playing, location, controller, Modifier.weight(1f))
                }
            } else {
                val artworkSize = (maxHeight - 215.dp).coerceIn(155.dp, 440.dp)
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MusicArtwork(
                        playing.title, Modifier.widthIn(max = artworkSize).fillMaxWidth().aspectRatio(1f), artwork,
                    )
                    Spacer(Modifier.height(20.dp))
                    PlaybackDetails(playing, location, controller)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackDetails(playing: NowPlaying, location: String, controller: MediaController?, modifier: Modifier = Modifier) {
    val seekable = playing.canSeek && playing.duration > 0 && controller != null
    var draft by remember(playing.id) { mutableStateOf<Float?>(null) }
    val seekInteraction = remember { MutableInteractionSource() }
    val seekFocused by seekInteraction.collectIsFocusedAsState()
    val seekInk = if (seekable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val seekRail = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth()) {
        Text(
            playing.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        )
        Text(
            playbackCaption(playing, location), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = draft ?: playing.fraction,
            onValueChange = { draft = it },
            onValueChangeFinished = {
                val requested = draft
                if (seekable && requested != null) controller.seekTo((requested * playing.duration).toLong())
                draft = null
            },
            modifier = Modifier.semantics { contentDescription = "再生位置" },
            enabled = seekable,
            interactionSource = seekInteraction,
            thumb = {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (seekFocused) Box(Modifier.size(22.dp).border(2.dp, seekInk, CircleShape))
                    Box(Modifier.size(12.dp).background(seekInk, CircleShape))
                }
            },
            track = { slider ->
                Box(Modifier.fillMaxWidth().height(4.dp).background(seekRail, CircleShape)) {
                    Box(Modifier.fillMaxWidth(slider.value).height(4.dp).background(seekInk, CircleShape))
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeLabel(draft?.let { (it * playing.duration).toLong() } ?: playing.position)
            TimeLabel(playing.duration)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { controller?.seekToPrevious() }, enabled = playing.canPrevious && controller != null) {
                MusicGlyph(Glyph.Previous, Modifier.size(25.dp), "前へ")
            }
            FilledIconButton(onClick = { controller?.togglePlayback() }, enabled = controller != null, modifier = Modifier.size(64.dp)) {
                PlayPauseGlyph(playing, Modifier.size(32.dp))
            }
            IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = playing.canNext && controller != null) {
                MusicGlyph(Glyph.Next, Modifier.size(25.dp), "次へ")
            }
        }
    }
}

private val NowPlaying.fraction get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

private fun playbackCaption(playing: NowPlaying, location: String) = when {
    playing.failed -> "この曲を再生できません"
    playing.buffering -> "読み込み中…"
    else -> location
}

@Composable
private fun PlayPauseGlyph(playing: NowPlaying, modifier: Modifier) {
    MusicGlyph(
        if (playing.wantsPlayback) Glyph.Pause else Glyph.Play, modifier,
        if (playing.wantsPlayback) "一時停止" else "再生",
    )
}

@Composable
private fun TimeLabel(ms: Long) {
    val seconds = ms / 1_000
    Text(
        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60),
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
