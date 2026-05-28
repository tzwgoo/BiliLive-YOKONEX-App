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
        assertEquals(3, DashboardUiState().recentEventSections.size)
    }

    @Test
    fun buildDashboardRecentEvents_keepsLatestGiftAndLikeWhenDanmakuFloodsFeed() {
        val logs = buildList {
            repeat(10) { index ->
                add(
                    EventLogEntity(
                        id = "danmaku-$index",
                        eventType = "DANMAKU",
                        summary = "弹幕 $index",
                        rawPayloadJson = "{}",
                        matchedRuleId = null,
                        outputMode = "BLUETOOTH",
                        outputSuccess = false,
                        outputMessage = "no_matching_rule",
                        createdAt = 1_714_113_037_000L - index,
                    ),
                )
            }
            add(
                EventLogEntity(
                    id = "like-1",
                    eventType = "LIKE",
                    summary = "点赞事件",
                    rawPayloadJson = "{}",
                    matchedRuleId = null,
                    outputMode = "BLUETOOTH",
                    outputSuccess = false,
                    outputMessage = "no_matching_rule",
                    createdAt = 1_714_113_036_000L,
                ),
            )
            add(
                EventLogEntity(
                    id = "gift-1",
                    eventType = "GIFT",
                    summary = "礼物事件",
                    rawPayloadJson = "{}",
                    matchedRuleId = null,
                    outputMode = "BLUETOOTH",
                    outputSuccess = true,
                    outputMessage = "ok",
                    createdAt = 1_714_113_035_000L,
                ),
            )
        }

        val recentEvents = buildDashboardRecentEvents(logs)

        assertEquals(10, recentEvents.size)
        assertEquals(true, recentEvents.any { it.id == "like-1" })
        assertEquals(true, recentEvents.any { it.id == "gift-1" })
    }

    @Test
    fun buildDashboardEventSections_groupsGiftLikeAndDanmakuIndependently() {
        val logs = listOf(
            dashboardEntity(id = "gift-1", eventType = "GIFT", createdAt = 30L),
            dashboardEntity(id = "like-1", eventType = "LIKE", createdAt = 20L),
            dashboardEntity(id = "danmaku-1", eventType = "DANMAKU", createdAt = 10L),
            dashboardEntity(id = "gift-2", eventType = "GIFT", createdAt = 5L),
        )

        val sections = buildDashboardEventSections(logs)

        assertEquals(listOf("礼物", "点赞", "弹幕"), sections.map { it.title })
        assertEquals(listOf("gift-1", "gift-2"), sections[0].events.map { it.id })
        assertEquals(listOf("like-1"), sections[1].events.map { it.id })
        assertEquals(listOf("danmaku-1"), sections[2].events.map { it.id })
    }

    private fun dashboardEntity(
        id: String,
        eventType: String,
        createdAt: Long,
    ) = EventLogEntity(
        id = id,
        eventType = eventType,
        summary = id,
        rawPayloadJson = "{}",
        matchedRuleId = null,
        outputMode = "BLUETOOTH",
        outputSuccess = eventType != "LIKE",
        outputMessage = if (eventType == "LIKE") "no_matching_rule" else "ok",
        createdAt = createdAt,
    )
}
