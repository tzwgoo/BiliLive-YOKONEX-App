package com.yokonex.bililive.app.ui.rules

import com.yokonex.bililive.app.ui.MainDispatcherRule
import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.storage.DefaultWaveforms
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.data.storage.entity.WaveformEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RulesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    @Test
    fun waveformOptions_followDaoUpdates() = runTest {
        val waveformDao = FakeWaveformDao()
        val viewModel = RulesViewModel(
            ruleStore = null,
            waveformDao = waveformDao,
        )

        waveformDao.emit(
            listOf(
                WaveformMapper.toEntity(
                    DefaultWaveforms.all.first().copy(
                        id = "custom-wave-01",
                        name = "自定义波形 01",
                        builtin = false,
                    ),
                ),
            ),
        )

        val firstRule = viewModel.uiState.value.rules.first()
        assertTrue(firstRule.waveformOptions.any { it.id == "custom-wave-01" })
    }

    private class FakeWaveformDao : WaveformDao {
        private val state = MutableStateFlow<List<WaveformEntity>>(emptyList())

        override fun observeAll(): Flow<List<WaveformEntity>> = state

        override suspend fun count(): Int = state.value.size

        override suspend fun insertAll(waveforms: List<WaveformEntity>) {
            state.value = waveforms
        }

        override suspend fun upsert(waveform: WaveformEntity) {
            state.value = state.value.filterNot { it.id == waveform.id } + waveform
        }

        override suspend fun findById(id: String): WaveformEntity? =
            state.value.firstOrNull { it.id == id }

        override suspend fun deleteById(id: String) {
            state.value = state.value.filterNot { it.id == id }
        }

        suspend fun emit(waveforms: List<WaveformEntity>) {
            state.value = waveforms
        }
    }
}
