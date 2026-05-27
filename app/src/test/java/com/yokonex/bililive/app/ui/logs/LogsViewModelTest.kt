package com.yokonex.bililive.app.ui.logs

import org.junit.Assert.assertFalse
import org.junit.Test

class LogsViewModelTest {

    @Test
    fun defaultState_containsRecentLogs() {
        val viewModel = LogsViewModel()

        assertFalse(viewModel.uiState.value.logs.isEmpty())
    }
}
