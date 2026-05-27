package com.yokonex.bililive.app.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveConfigViewModelTest {

    @Test
    fun updateRoomId_updatesUiState() {
        val viewModel = LiveConfigViewModel()

        viewModel.updateRoomId("22445566")

        assertEquals("22445566", viewModel.uiState.value.roomId)
    }

    @Test
    fun toggleAutoReconnect_updatesFlag() {
        val viewModel = LiveConfigViewModel()

        viewModel.toggleAutoReconnect(true)

        assertTrue(viewModel.uiState.value.autoReconnect)
    }
}
