package dev.musicswitcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFilesTest {
    @Test
    fun folderIncludesAudioButNeverStagingFilesOrVideos() {
        assertTrue(isAudioFile("曲.MP3", "application/octet-stream"))
        assertTrue(isAudioFile("音声", "audio/aac"))
        assertTrue(isAudioFile("取得済み.m4a", "application/octet-stream"))
        assertFalse(isAudioFile("MusicSwitcher-test.partial", "audio/mp4"))
        assertFalse(isAudioFile("movie.mp4", "video/mp4"))
    }

    @Test
    fun downloadDoesNotChooseAnExistingName() {
        assertEquals("音楽 (3).m4a", uniqueAudioName("音楽.m4a", setOf("音楽.m4a", "音楽 (2).m4a")))
        assertEquals("SONG (2).m4a", uniqueAudioName("SONG.m4a", setOf("song.m4a")))
        assertEquals("Live (2).opus", uniqueAudioName("Live.opus", setOf("live.opus")))
    }

    @Test
    fun audioExtensionsWorkWhenTheProviderHasNoUsefulMimeType() {
        for (name in listOf("曲.FLAC", "voice.AMR", "audio.OGA", "file.OPUS", "recording.WAV", "audio.MKA")) {
            assertTrue(name, isAudioFile(name, ""))
        }
        assertFalse(isAudioFile("notes.txt", "application/octet-stream"))
        assertFalse(isAudioFile("no-extension", "application/octet-stream"))
    }

    @Test
    fun unfinishedFilesAreExcludedEvenWhenTheProviderReportsAudio() {
        for (name in listOf("track.m4a.PART", "track.partial", "一時ファイル.TMP")) {
            assertFalse(name, isAudioFile(name, "audio/mp4"))
        }
    }

    @Test
    fun unusedDownloadNameKeepsCaseAndPunctuation() {
        assertEquals("曲・第1楽章.m4a", uniqueAudioName("曲・第1楽章.m4a", emptySet()))
        assertEquals("song.m4a", uniqueAudioName("song.m4a", setOf("song (2).m4a")))
    }

    @Test
    fun collisionUsesFirstFreeSuffixNotMaximumSuffix() {
        val occupied = setOf("mix.m4a", "mix (2).m4a", "mix (4).m4a", "mix (6).m4a")
        assertEquals("Mix (3).m4a", uniqueAudioName("Mix.m4a", occupied))
    }
}
