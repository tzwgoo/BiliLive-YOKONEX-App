package com.yokonex.bililive.app.ui.waveforms

import com.yokonex.bililive.app.ui.MainDispatcherRule
import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.storage.JsonRuleStore
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.data.storage.entity.WaveformEntity
import com.yokonex.bililive.data.waveform.DefaultWaveformLibraryRepository
import com.yokonex.bililive.data.waveform.WaveformLibraryRepository
import com.yokonex.bililive.domain.model.WaveformDefinition
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaveformsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_selectsFirstWaveformAndBuildsDraft() = runTest {
        val firstWaveform = sampleWaveform("ems-preset-01", "预设一", builtin = true)
        val viewModel = createViewModel(
            waveforms = listOf(firstWaveform),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("ems-preset-01", state.selectedWaveformId)
        assertEquals("ems-preset-01", state.draftWaveform?.id)
        assertFalse(state.isEditorVisible)
    }

    @Test
    fun selectWaveform_opensEditorPage() = runTest {
        val firstWaveform = sampleWaveform("ems-preset-01", "预设一", builtin = true)
        val secondWaveform = sampleWaveform("custom-wave-01", "自定义一", builtin = false)
        val viewModel = createViewModel(
            waveforms = listOf(firstWaveform, secondWaveform),
        )
        advanceUntilIdle()

        viewModel.selectWaveform("custom-wave-01")

        val state = viewModel.uiState.value
        assertEquals("custom-wave-01", state.selectedWaveformId)
        assertEquals("custom-wave-01", state.draftWaveform?.id)
        assertTrue(state.isEditorVisible)
    }

    @Test
    fun createWaveform_opensEditorPageForNewWaveform() = runTest {
        val firstWaveform = sampleWaveform("ems-preset-01", "预设一", builtin = true)
        val viewModel = createViewModel(
            waveforms = listOf(firstWaveform),
        )
        advanceUntilIdle()

        viewModel.createWaveform()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEditorVisible)
        assertTrue(state.selectedWaveformId.startsWith("custom-wave-"))
        assertEquals(state.selectedWaveformId, state.draftWaveform?.id)
    }

    @Test
    fun saveDraft_clearsDirtyFlag() = runTest {
        val customWaveform = sampleWaveform("custom-wave-01", "初始名字", builtin = false)
        val viewModel = createViewModel(
            waveforms = listOf(customWaveform),
        )
        advanceUntilIdle()

        viewModel.updateWaveformName("新的名字")
        assertEquals(true, viewModel.uiState.value.isDirty)

        viewModel.saveDraft()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDirty)
        assertEquals("新的名字", viewModel.uiState.value.draftWaveform?.name)
    }

    @Test
    fun duplicateSelectedWaveform_selectsDuplicatedCopy() = runTest {
        val sourceWaveform = sampleWaveform("custom-wave-01", "原始波形", builtin = false)
        val viewModel = createViewModel(
            waveforms = listOf(sourceWaveform),
        )
        advanceUntilIdle()

        viewModel.duplicateSelectedWaveform()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotEquals("custom-wave-01", state.selectedWaveformId)
        assertEquals(state.selectedWaveformId, state.draftWaveform?.id)
        assertEquals(false, state.draftWaveform?.builtin)
    }

    @Test
    fun updateDraftStrength_marksDirtyAndChangesChannel() = runTest {
        val customWaveform = sampleWaveform("custom-wave-01", "可编辑波形", builtin = false)
        val viewModel = createViewModel(
            waveforms = listOf(customWaveform),
        )
        advanceUntilIdle()

        viewModel.updateDraftStrength(
            stepIndex = 0,
            channel = WaveformChannel.A,
            strength = 88,
        )

        assertEquals(88, viewModel.uiState.value.draftWaveform?.steps?.first()?.channelA)
        assertEquals(true, viewModel.uiState.value.isDirty)
    }

    @Test
    fun insertStep_addsOneStepAfterGivenBoundary() = runTest {
        val customWaveform = sampleWaveform("custom-wave-01", "可编辑波形", builtin = false)
        val viewModel = createViewModel(
            waveforms = listOf(customWaveform),
        )
        advanceUntilIdle()

        viewModel.insertStep(insertIndex = 1)

        assertEquals(2, viewModel.uiState.value.draftWaveform?.steps?.size)
        assertEquals(true, viewModel.uiState.value.isDirty)
    }

    @Test
    fun appendAndRemoveStep_updateDraftStepCount() = runTest {
        val customWaveform = sampleWaveform("custom-wave-01", "可编辑波形", builtin = false)
        val viewModel = createViewModel(
            waveforms = listOf(customWaveform),
        )
        advanceUntilIdle()

        viewModel.appendStep()
        assertEquals(2, viewModel.uiState.value.draftWaveform?.steps?.size)

        viewModel.removeLastStep()
        assertEquals(1, viewModel.uiState.value.draftWaveform?.steps?.size)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun reopenSameWaveform_keepsDirtyDraft() = runTest {
        val customWaveform = sampleWaveform("custom-wave-01", "可编辑波形", builtin = false)
        val viewModel = createViewModel(
            waveforms = listOf(customWaveform),
        )
        advanceUntilIdle()

        viewModel.selectWaveform("custom-wave-01")
        viewModel.updateWaveformName("未保存的新名字")
        viewModel.closeEditor()
        viewModel.selectWaveform("custom-wave-01")

        val state = viewModel.uiState.value
        assertEquals("未保存的新名字", state.draftWaveform?.name)
        assertTrue(state.isDirty)
        assertTrue(state.isEditorVisible)
    }

    private fun createViewModel(
        waveforms: List<WaveformDefinition>,
    ): WaveformsViewModel {
        val waveformDao = FakeWaveformDao(
            initial = waveforms.map(WaveformMapper::toEntity),
        )
        val repository: WaveformLibraryRepository = DefaultWaveformLibraryRepository(
            waveformDao = waveformDao,
            ruleStore = JsonRuleStore(
                file = Files.createTempFile("waveforms-view-model-rules", ".json").toFile(),
                defaultRules = emptyList(),
            ),
        )
        return WaveformsViewModel(
            waveformDao = waveformDao,
            waveformLibraryRepository = repository,
        )
    }

    private fun sampleWaveform(
        id: String,
        name: String,
        builtin: Boolean,
    ): WaveformDefinition =
        WaveformDefinition(
            id = id,
            name = name,
            builtin = builtin,
            steps = listOf(
                com.yokonex.bililive.domain.model.WaveformStep(
                    durationMs = 200,
                    channelA = 10,
                    channelB = 20,
                ),
            ),
        )

    private class FakeWaveformDao(
        initial: List<WaveformEntity>,
    ) : WaveformDao {
        private val state = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<WaveformEntity>> = state

        override suspend fun count(): Int = state.value.size

        override suspend fun insertAll(waveforms: List<WaveformEntity>) {
            state.value = waveforms
        }

        override suspend fun upsert(waveform: WaveformEntity) {
            state.value = (state.value.filterNot { it.id == waveform.id } + waveform)
                .sortedBy(WaveformEntity::name)
        }

        override suspend fun findById(id: String): WaveformEntity? =
            state.value.firstOrNull { it.id == id }

        override suspend fun deleteById(id: String) {
            state.value = state.value.filterNot { it.id == id }
        }
    }
}
