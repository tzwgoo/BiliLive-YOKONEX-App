package com.yokonex.bililive.app.ui.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun giftRule_exposesEditablePriceRangeAndWaveformSelection() {
        val viewModel = RulesViewModel()
        val giftRule = viewModel.uiState.value.rules.first { rule -> rule.id == "rule_gift_combo" }

        assertTrue(giftRule.canEditGiftPriceRange)
        assertTrue(giftRule.waveformOptions.isNotEmpty())
        assertTrue(giftRule.imSlotLabel.isNotBlank())
    }
}
