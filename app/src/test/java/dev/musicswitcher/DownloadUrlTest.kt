package dev.musicswitcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadUrlTest {
    @Test
    fun acceptsOneHttpsUrlAndPreservesItsQuery() {
        val url = "https://example.com/song?a=1&b=2"
        assertEquals(url, parseDownloadUrl(" $url\n"))
    }

    @Test
    fun rejectsOptionsCredentialsAndNonPublicInputForms() {
        listOf(
            "--exec something", "file:///sdcard/music.mp3", "http://example.com/a",
            "https://user:password@example.com/a", "https://localhost/a",
            "https://127.0.0.1/a", "https://[::1]/a", "https://player.local/a",
            "https://example.com:8443/a", "https://example.com/a https://example.com/b",
        ).forEach { assertNull(it, parseDownloadUrl(it)) }
    }
}
