package dev.musicswitcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRefreshStateTest {
    @Test
    fun refreshRemainsAvailableAfterASelectedFolderCannotBeScanned() {
        val state = MusicUiState(hasFolderSelection = true, folderError = "Folder unavailable")
        assertTrue(state.canRefresh)
    }

    @Test
    fun withoutASelectionAnErrorDoesNotEnableRefresh() {
        assertFalse(MusicUiState(folderError = "Permission unavailable").canRefresh)
    }

    @Test
    fun refreshIsDisabledWhileScanning() {
        assertFalse(MusicUiState(hasFolderSelection = true, scanning = true).canRefresh)
    }
}
