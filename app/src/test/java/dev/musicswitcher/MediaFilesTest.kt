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
    }
}
