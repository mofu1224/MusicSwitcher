package dev.musicswitcher

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

internal data class NowPlaying(
    val id: String? = null,
    val title: String = "",
    val artist: String? = null,
    val artworkData: ByteArray? = null,
    val wantsPlayback: Boolean = false,
    val buffering: Boolean = false,
    val failed: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val canSeek: Boolean = false,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
)

internal data class PlaybackConnection(val controller: MediaController? = null, val failed: Boolean = false)

@Composable
internal fun rememberPlaybackConnection(): PlaybackConnection {
    val context = LocalContext.current
    var connection by remember { mutableStateOf(PlaybackConnection()) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var active = true
        future.addListener({
            if (active) {
                connection = runCatching { PlaybackConnection(future.get()) }
                    .getOrElse { PlaybackConnection(failed = true) }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            MediaController.releaseFuture(future)
        }
    }
    return connection
}

@Composable
internal fun rememberNowPlaying(controller: MediaController?): NowPlaying {
    var state by remember(controller) { mutableStateOf(controller?.snapshot() ?: NowPlaying()) }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                state = player.snapshot()
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller, state.wantsPlayback) {
        if (controller == null || !state.wantsPlayback) return@LaunchedEffect
        while (true) {
            delay(300)
            state = state.copy(position = controller.currentPosition.coerceAtLeast(0L))
        }
    }
    return state
}

internal fun Player.playTracks(tracks: List<Track>, startIndex: Int) {
    val items = tracks.map { track ->
        MediaItem.Builder()
            .setMediaId(track.uri.toString())
            .setUri(track.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
            .build()
    }
    setMediaItems(items, startIndex, C.TIME_UNSET)
    prepare()
    play()
}

internal fun Player.togglePlayback() {
    when {
        playbackState == Player.STATE_ENDED -> {
            seekToDefaultPosition()
            play()
        }
        playWhenReady && playerError == null -> pause()
        else -> {
            if (playbackState == Player.STATE_IDLE) prepare()
            play()
        }
    }
}

private fun Player.snapshot(): NowPlaying = NowPlaying(
    id = currentMediaItem?.mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() },
    artworkData = mediaMetadata.artworkData,
    wantsPlayback = playWhenReady && playbackState != Player.STATE_ENDED && playerError == null,
    buffering = playbackState == Player.STATE_BUFFERING,
    failed = playerError != null,
    position = currentPosition.coerceAtLeast(0L),
    duration = duration.coerceAtLeast(0L),
    canSeek = isCurrentMediaItemSeekable,
    canPrevious = hasPreviousMediaItem() || isCurrentMediaItemSeekable,
    canNext = hasNextMediaItem(),
)
