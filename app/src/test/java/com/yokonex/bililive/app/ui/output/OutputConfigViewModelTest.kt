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
        assertEquals("ws://103.236.55.92:43001/", viewModel.uiState.value.socketEndpoint)
    }

    @Test
    fun bluetoothPreviewState_defaultsToDisconnected() {
        val viewModel = OutputConfigViewModel()

        assertEquals("未连接", viewModel.uiState.value.bluetoothStatus)
        assertEquals(null, viewModel.uiState.value.bluetoothBatteryLevel)
        assertEquals("", viewModel.uiState.value.connectedBluetoothDeviceName)
        assertEquals(0, viewModel.uiState.value.channelAStrength)
        assertEquals(0, viewModel.uiState.value.channelBStrength)
    }
}
