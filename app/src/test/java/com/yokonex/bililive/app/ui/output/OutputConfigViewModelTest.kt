package com.yokonex.bililive.app.ui.output

import com.yokonex.bililive.app.ui.MainDispatcherRule
import com.yokonex.bililive.data.bluetooth.BluetoothRepository
import com.yokonex.bililive.data.bluetooth.model.BluetoothConnectionState
import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice
import com.yokonex.bililive.data.bluetooth.model.BluetoothRuntimeStatus
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class OutputConfigViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    @Test
    fun state_includesMixModeLeaderAndCap() = runTest {
        val repository = FakeOutputBluetoothRepository(
            runtimeStatusFlow = MutableStateFlow(
                BluetoothRuntimeStatus(
                    connected = true,
                    deviceName = "YYC-DJ-V2-003",
                    leaderEventType = LiveEventType.GIFT,
                    activeLayerCount = 3,
                    outputCap = 180,
                    mixedChannelAStrength = 150,
                    mixedChannelBStrength = 120,
                    mixModeEnabled = true,
                ),
            ),
        )
        val viewModel = OutputConfigViewModel(
            bluetoothRepository = repository,
        )

        val state = viewModel.uiState.value
        assertEquals("礼物主层", state.bluetoothLeaderLabel)
        assertEquals("180", state.bluetoothOutputCapLabel)
        assertEquals("混波", state.bluetoothMixModeLabel)
        assertEquals(3, state.activeBluetoothLayerCount)
        assertEquals(150, state.channelAStrength)
        assertEquals(120, state.channelBStrength)
    }
}

private class FakeOutputBluetoothRepository(
    runtimeStatusFlow: MutableStateFlow<BluetoothRuntimeStatus>,
) : BluetoothRepository {
    override val connectionState: StateFlow<BluetoothConnectionState> =
        MutableStateFlow(BluetoothConnectionState.CONNECTED)
    override val devices: StateFlow<List<BluetoothDevice>> =
        MutableStateFlow(emptyList())
    override val runtimeStatus: StateFlow<BluetoothRuntimeStatus> = runtimeStatusFlow

    override suspend fun scan(): List<BluetoothDevice> = emptyList()

    override suspend fun connect(deviceId: String) = Unit

    override suspend fun disconnect() = Unit

    override suspend fun playWaveform(
        waveformId: String,
        repeatCount: Int,
    ) = Unit

    override suspend fun enqueueWaveform(
        waveformId: String,
        eventType: LiveEventType,
        repeatCount: Int,
    ) = Unit

    override suspend fun clearActiveWaveforms() = Unit

    override fun setMixModeEnabled(enabled: Boolean) = Unit
}
