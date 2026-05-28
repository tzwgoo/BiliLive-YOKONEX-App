package com.yokonex.bililive.app.ui.live

import com.yokonex.bililive.app.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LiveConfigViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    @Test
    fun toggleMonitoring_whenIdle_updatesStatusToRunning() = runTest {
        val viewModel = LiveConfigViewModel()

        viewModel.toggleMonitoring()

        assertEquals("监听中", viewModel.uiState.value.monitoringStatus)
    }

    @Test
    fun updateLiveEventSettings_updatesPreviewState() {
        val viewModel = LiveConfigViewModel()

        viewModel.updateLikeMultiple("200")
        viewModel.updateDanmakuEnabled(true)
        viewModel.updateDanmakuKeywords("开火,冲冲冲")
        viewModel.updateDanmakuCooldownSeconds("6")

        assertEquals("200", viewModel.uiState.value.likeMultiple)
        assertTrue(viewModel.uiState.value.danmakuEnabled)
        assertEquals("开火,冲冲冲", viewModel.uiState.value.danmakuKeywords)
        assertEquals("6", viewModel.uiState.value.danmakuCooldownSeconds)
    }

    @Test
    fun parseDanmakuKeywords_supportsCommaVariantsAndLineBreaks() {
        assertEquals(
            listOf("开火", "冲冲冲", "加速"),
            parseDanmakuKeywords("开火，冲冲冲\n加速"),
        )
    }
}
