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

    @Test
    fun acceptsExplicitHttpsPortAndKeepsEncodedPathAndQuery() {
        val url = "HTTPS://media.example.com:443/%E6%9B%B2.m4a?name=a%2Bb#start"
        assertEquals(url, parseDownloadUrl(url))
    }

    @Test
    fun rejectsMissingAuthorityAndEmbeddedWhitespace() {
        listOf("", " \t\n ", "https:/example.com/a", "https:///example.com/a", "//example.com/a",
            "https://example.com/a\tb", "https://example.com/a\nb", "https://example.com:0/a",
        ).forEach { assertNull(parseDownloadUrl(it)) }
    }

    @Test
    fun rejectsLocalSuffixesEvenWithCaseAndTrailingDot() {
        listOf(
            "https://host.LOCAL./audio", "https://host.Internal./audio",
            "https://host.LocalHost./audio", "https://singlelabel/audio",
        ).forEach { assertNull(it, parseDownloadUrl(it)) }
    }

    @Test
    fun enforcesLengthLimitAfterTrimming() {
        val base = "https://example.com/"
        val atLimit = base.padEnd(4_096, 'x')
        assertEquals(atLimit, parseDownloadUrl(" $atLimit "))
        assertNull(parseDownloadUrl("${atLimit}x"))
    }
}
