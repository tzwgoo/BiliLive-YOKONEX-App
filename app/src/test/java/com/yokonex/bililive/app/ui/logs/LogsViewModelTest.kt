package com.yokonex.bililive.app.ui.logs

import com.yokonex.bililive.data.storage.entity.EventLogEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogsViewModelTest {

    @Test
    fun defaultState_containsRecentLogs() {
        val viewModel = LogsViewModel()

        assertFalse(viewModel.uiState.value.logs.isEmpty())
    }

    @Test
    fun filterLogs_returnsOnlySelectedEventType() {
        val logs = listOf(
            logEntity(id = "gift-1", eventType = "SUPER_CHAT"),
            logEntity(id = "like-1", eventType = "LIKE"),
            logEntity(id = "system-1", eventType = "SYSTEM"),
        )

        val filtered = filterLogs(logs, LogEventFilter.GIFT)

        assertEquals(1, filtered.size)
        assertTrue(filtered.all { it.eventType == "SUPER_CHAT" })
    }

    @Test
    fun filterLogs_returnsAllLogsForAllFilter() {
        val logs = listOf(
            logEntity(id = "gift-1", eventType = "GIFT"),
            logEntity(id = "like-1", eventType = "LIKE"),
        )

        val filtered = filterLogs(logs, LogEventFilter.ALL)

        assertEquals(2, filtered.size)
    }

    @Test
    fun toUiEventLog_mapsUserLimitSkippedStatus() {
        val uiLog = toUiEventLog(
            logEntity(
                id = "danmaku-1",
                eventType = "DANMAKU",
                outputSuccess = false,
                outputMessage = "user_limit_skipped",
            ),
        )

        assertEquals("限流跳过", uiLog.statusLabel)
    }

    private fun logEntity(
        id: String,
        eventType: String,
        outputSuccess: Boolean = true,
        outputMessage: String = "ok",
    ) = EventLogEntity(
        id = id,
        eventType = eventType,
        summary = id,
        rawPayloadJson = "{}",
        matchedRuleId = null,
        outputMode = "BLUETOOTH",
        outputSuccess = outputSuccess,
        outputMessage = outputMessage,
        createdAt = 1L,
    )
}
