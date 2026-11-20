package dev.musicswitcher

import android.content.Context
import android.util.AtomicFile
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

internal class AudioDownloader(private val context: Context) {
    suspend fun fetch(url: String, workspace: File, onProgress: (Int?) -> Unit): File =
        runInterruptible(Dispatchers.IO) {
            val engine = YoutubeDL.getInstance()
            synchronized(initLock) {
                FFmpeg.getInstance().init(context)
                installPinnedCore()
                engine.init(context)
            }
            val request = YoutubeDLRequest(url)
                .addOption("--ignore-config")
                .addOption("--no-playlist")
                .addOption("--playlist-items", "1")
                .addOption("--no-remote-components")
                .addOption("--match-filters", "!is_live")
                .addOption("--newline")
                .addOption("--socket-timeout", 15)
                .addOption("--retries", 2)
                .addOption("--fragment-retries", 2)
                .addOption("--max-filesize", "200M")
                .addOption("--format", "bestaudio/best")
                .addOption("--extract-audio")
                .addOption("--audio-format", "best")
                // Even for an unknown codec, never fall back to a lossy encoder.
                .addOption("--postprocessor-args", "ExtractAudio+ffmpeg_o:-c:a copy")
                .addOption("--windows-filenames")
                .addOption("--trim-filenames", 80)
                .addOption("--paths", workspace.absolutePath)
                .addOption("--output", "%(title).80B.%(ext)s")
                .addCommands(listOf("--"))
            engine.execute(request, UUID.randomUUID().toString()) { value, _, _ ->
                onProgress(value.takeIf { it.isFinite() && it in 0f..100f }?.toInt())
            }
            workspace.listFiles()?.singleOrNull { it.isFile && isAudioFile(it.name, "") && it.length() > 0 }
                ?: throw IOException("No completed audio output")
        }

    private companion object { val initLock = Any() }

    private fun installPinnedCore() {
        // youtubedl-android 0.18.1 otherwise keeps the first engine copy across APK updates.
        val path = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
        val atomic = AtomicFile(path)
        val installedHash = try {
            atomic.openRead().use { file ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = file.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (_: java.io.FileNotFoundException) {
            null
        }
        if (installedHash == BuildConfig.YT_DLP_SHA256) return
        val output = atomic.startWrite()
        try {
            context.resources.openRawResource(R.raw.ytdlp).use { it.copyTo(output) }
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }
}
