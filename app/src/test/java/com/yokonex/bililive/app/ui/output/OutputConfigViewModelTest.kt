package com.yokonex.bililive.app.ui.output

import com.yokonex.bililive.domain.model.OutputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OutputConfigViewModelTest {

    @Test
    fun selectMode_switchesCurrentOutputMode() {
        val viewModel = OutputConfigViewModel()

        viewModel.selectMode(OutputMode.WEBSOCKET)

        assertEquals(OutputMode.WEBSOCKET, viewModel.uiState.value.outputMode)
    }

    @Test
    fun defaultState_containsBluetoothDevices() {
        val viewModel = OutputConfigViewModel()

        assertFalse(viewModel.uiState.value.bluetoothDevices.isEmpty())
    }
}
