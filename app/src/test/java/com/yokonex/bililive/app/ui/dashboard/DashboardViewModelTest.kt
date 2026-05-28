package com.yokonex.bililive.app.ui.dashboard

import com.yokonex.bililive.app.ui.MainDispatcherRule
import com.yokonex.bililive.data.storage.entity.EventLogEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun defaultState_exposesBluetoothRuntimeSummary() {
        val viewModel = DashboardViewModel()

        assertEquals(null, viewModel.uiState.value.bluetoothBatteryLevel)
        assertEquals(0, viewModel.uiState.value.channelAStrength)
        assertEquals(0, viewModel.uiState.value.channelBStrength)
    }

    @Test
    fun dashboardHome_doesNotExposeMonitoringButtonLabel() {
        val viewModel = DashboardViewModel()

        assertEquals("蓝牙 EMS", viewModel.uiState.value.outputModeLabel)
    }

    @Test
    fun toDashboardEventLog_exposesRealtimeDanmakuStatus() {
        val eventLog = toDashboardEventLog(
            EventLogEntity(
                id = "log-1",
                eventType = "DANMAKU",
                summary = "测试用户 发送弹幕 开火",
                rawPayloadJson = "{}",
                matchedRuleId = "danmaku-default",
                outputMode = "BLUETOOTH",
                outputSuccess = false,
                outputMessage = "cooldown_skipped",
                createdAt = 1_714_113_037_000L,
            ),
        )

        assertEquals("实时弹幕", eventLog.title)
        assertEquals("冷却跳过", eventLog.statusLabel)
    }

    @Test
    fun toDashboardEventLog_marksTriggeredGiftAsSuccess() {
        val eventLog = toDashboardEventLog(
            EventLogEntity(
                id = "log-2",
                eventType = "GIFT",
                summary = "测试用户 送出 小电视 x1",
                rawPayloadJson = "{}",
                matchedRuleId = "gift-tier-01",
                outputMode = "BLUETOOTH",
                outputSuccess = true,
                outputMessage = "ok",
                createdAt = 1_714_113_037_000L,
            ),
        )

        assertEquals("实时礼物", eventLog.title)
        assertEquals("已触发", eventLog.statusLabel)
    }

    @Test
    fun normalizeAnchorName_usesFallbackWhenBlank() {
        assertEquals("未获取主播名称", normalizeAnchorName("   "))
    }

    @Test
    fun normalizeEventTimestampMillis_convertsSecondPrecisionToMillis() {
        assertEquals(1_714_113_037_000L, normalizeEventTimestampMillis(1_714_113_037L))
    }

    @Test
    fun sampleRecentEvents_showsMoreRealtimeItems() {
        assertEquals(4, DashboardUiState().recentEvents.size)
    }
}
