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
            logEntity(id = "gift-1", eventType = "GIFT"),
            logEntity(id = "like-1", eventType = "LIKE"),
            logEntity(id = "system-1", eventType = "SYSTEM"),
        )

        val filtered = filterLogs(logs, LogEventFilter.LIKE)

        assertEquals(1, filtered.size)
        assertTrue(filtered.all { it.eventType == "LIKE" })
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

    private fun logEntity(
        id: String,
        eventType: String,
    ) = EventLogEntity(
        id = id,
        eventType = eventType,
        summary = id,
        rawPayloadJson = "{}",
        matchedRuleId = null,
        outputMode = "BLUETOOTH",
        outputSuccess = true,
        outputMessage = "ok",
        createdAt = 1L,
    )
}
