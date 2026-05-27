package com.yokonex.bililive.app.ui.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class RulesViewModelTest {

    @Test
    fun toggleRule_flipsEnabledStateForSelectedRule() {
        val viewModel = RulesViewModel()
        val initialRule = viewModel.uiState.value.rules.first()

        viewModel.toggleRule(initialRule.id)

        val updatedRule = viewModel.uiState.value.rules.first { rule -> rule.id == initialRule.id }
        assertEquals(!initialRule.enabled, updatedRule.enabled)
    }
}
