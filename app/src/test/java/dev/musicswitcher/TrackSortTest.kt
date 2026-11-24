package dev.musicswitcher

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackSortTest {
    private data class Item(val id: String, val title: String, val directory: String = "")

    private val scanned = listOf(
        Item("1", "banana.mp3"), Item("2", "Apple.mp3"), Item("3", "cherry.mp3", "b"), Item("4", "apple.mp3", "a"),
    )

    private fun arrange(sort: TrackSort, stored: String?) =
        arrangeTracks(scanned, sort, stored, { it.title }, { it.directory }, { it.id }).map { it.id }

    @Test
    fun nameSortIgnoresCaseAndIsStable() {
        assertEquals(listOf("2", "4", "1", "3"), arrange(TrackSort.Name, "3\n1\n2\n4"))
    }

    @Test
    fun manualOrderSurvivesSwitchingToNameAndBack() {
        val manual = "3\n1\n4\n2"
        assertEquals(listOf("3", "1", "4", "2"), arrange(TrackSort.Manual, manual))
        arrange(TrackSort.Name, manual)
        assertEquals(listOf("3", "1", "4", "2"), arrange(TrackSort.Manual, manual))
    }

    @Test
    fun manualWithoutSavedOrderKeepsScanOrderAndNewTracksGoLast() {
        assertEquals(listOf("1", "2", "3", "4"), arrange(TrackSort.Manual, null))
        assertEquals(listOf("4", "2", "1", "3"), arrange(TrackSort.Manual, "4\n2\ngone"))
    }

    @Test
    fun unknownPresetFallsBackToManual() {
        assertEquals(TrackSort.Manual, TrackSort.of(null))
        assertEquals(TrackSort.Manual, TrackSort.of("bogus"))
        assertEquals(TrackSort.Name, TrackSort.of("name"))
    }
}
