package com.yokonex.bililive.app.ui.dashboard

import com.yokonex.bililive.app.ui.MainDispatcherRule
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.service.ServiceStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun toggleMonitoring_whenIdle_updatesStatusToRunning() = runTest {
        val viewModel = DashboardViewModel()

        viewModel.toggleMonitoring()

        assertTrue(viewModel.uiState.value.serviceStatus is ServiceStatus.Running)
    }

    @Test
    fun selectOutputMode_updatesSelectedMode() {
        val viewModel = DashboardViewModel()

        viewModel.selectOutputMode(OutputMode.WEBSOCKET)

        assertEquals(OutputMode.WEBSOCKET, viewModel.uiState.value.outputMode)
    }
}
