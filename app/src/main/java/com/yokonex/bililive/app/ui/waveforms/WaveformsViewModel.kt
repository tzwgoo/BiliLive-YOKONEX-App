package com.yokonex.bililive.app.ui.waveforms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.data.waveform.WaveformLibraryRepository
import com.yokonex.bililive.domain.model.WaveformDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WaveformsViewModel(
    private val waveformDao: WaveformDao? = AppServices.container?.waveformDao,
    private val waveformLibraryRepository: WaveformLibraryRepository? = AppServices.container?.waveformLibraryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WaveformsUiState())
    val uiState: StateFlow<WaveformsUiState> = _uiState.asStateFlow()

    private var currentWaveforms: List<WaveformDefinition> = emptyList()

    init {
        waveformDao?.let { dao ->
            viewModelScope.launch {
                dao.observeAll().collect { entities ->
                    currentWaveforms = entities.map(WaveformMapper::fromEntity)
                    syncUiState()
                }
            }
        }
    }

    fun updateWaveformName(name: String) {
        _uiState.update { current ->
            val draft = current.draftWaveform ?: return@update current
            current.copy(
                draftWaveform = draft.copy(name = name),
                isDirty = true,
            )
        }
    }

    fun selectWaveform(waveformId: String) {
        _uiState.update { current ->
            if (current.isDirty && current.selectedWaveformId == waveformId && current.draftWaveform?.id == waveformId) {
                current.copy(
                    isEditorVisible = true,
                    pendingSelectionWaveformId = null,
                )
            } else if (current.isDirty && current.selectedWaveformId != waveformId) {
                current.copy(
                    pendingSelectionWaveformId = waveformId,
                    editorMessage = "当前波形还有未保存更改",
                )
            } else {
                val selected = currentWaveforms.firstOrNull { it.id == waveformId } ?: return@update current
                current.copy(
                    selectedWaveformId = waveformId,
                    draftWaveform = selected,
                    isDirty = false,
                    pendingSelectionWaveformId = null,
                    isEditorVisible = true,
                )
            }
        }
    }

    fun confirmPendingSelection() {
        val targetId = _uiState.value.pendingSelectionWaveformId ?: return
        val selected = currentWaveforms.firstOrNull { it.id == targetId } ?: return
        _uiState.update { current ->
            current.copy(
                selectedWaveformId = targetId,
                draftWaveform = selected,
                isDirty = false,
                pendingSelectionWaveformId = null,
                isEditorVisible = true,
            )
        }
    }

    fun dismissPendingSelection() {
        _uiState.update { current ->
            current.copy(pendingSelectionWaveformId = null)
        }
    }

    fun createWaveform() {
        val repository = waveformLibraryRepository ?: return
        viewModelScope.launch {
            val created = repository.createWaveform()
            _uiState.update { current ->
                current.copy(
                    selectedWaveformId = created.id,
                    draftWaveform = created,
                    isDirty = false,
                    editorMessage = "已创建新波形",
                    isEditorVisible = true,
                )
            }
        }
    }

    fun closeEditor() {
        _uiState.update { current ->
            current.copy(
                isEditorVisible = false,
                pendingSelectionWaveformId = null,
            )
        }
    }

    fun updateDraftStrength(
        stepIndex: Int,
        channel: WaveformChannel,
        strength: Int,
    ) {
        updateDraftStep(stepIndex) { step ->
            when (channel) {
                WaveformChannel.A -> step.copy(channelA = strength.coerceIn(0, 180))
                WaveformChannel.B -> step.copy(channelB = strength.coerceIn(0, 180))
            }
        }
    }

    fun updateStepDuration(
        stepIndex: Int,
        durationMs: Int,
    ) {
        updateDraftStep(stepIndex) { step ->
            step.copy(durationMs = durationMs.coerceAtLeast(1))
        }
    }

    fun insertStep(insertIndex: Int) {
        _uiState.update { current ->
            val draft = current.draftWaveform ?: return@update current
            val safeIndex = insertIndex.coerceIn(0, draft.steps.size)
            val templateStep = draft.steps.getOrNull((safeIndex - 1).coerceAtLeast(0)) ?: defaultStep()
            val nextSteps = draft.steps.toMutableList().apply {
                add(safeIndex, templateStep.copy())
            }
            current.copy(
                draftWaveform = draft.copy(steps = nextSteps),
                isDirty = true,
            )
        }
    }

    fun appendStep() {
        val insertIndex = _uiState.value.draftWaveform?.steps?.size ?: return
        insertStep(insertIndex)
    }

    fun duplicateStep(stepIndex: Int) {
        _uiState.update { current ->
            val draft = current.draftWaveform ?: return@update current
            val sourceStep = draft.steps.getOrNull(stepIndex) ?: return@update current
            val nextSteps = draft.steps.toMutableList().apply {
                add(stepIndex + 1, sourceStep.copy())
            }
            current.copy(
                draftWaveform = draft.copy(steps = nextSteps),
                isDirty = true,
            )
        }
    }

    fun deleteStep(stepIndex: Int) {
        _uiState.update { current ->
            val draft = current.draftWaveform ?: return@update current
            if (draft.steps.size <= 1 || stepIndex !in draft.steps.indices) {
                return@update current.copy(editorMessage = "波形至少需要保留一个分段")
            }
            val nextSteps = draft.steps.toMutableList().apply {
                removeAt(stepIndex)
            }
            current.copy(
                draftWaveform = draft.copy(steps = nextSteps),
                isDirty = true,
            )
        }
    }

    fun removeLastStep() {
        val lastIndex = (_uiState.value.draftWaveform?.steps?.lastIndex ?: -1)
        if (lastIndex >= 0) {
            deleteStep(lastIndex)
        }
    }

    fun saveDraft() {
        val repository = waveformLibraryRepository ?: return
        val draft = _uiState.value.draftWaveform ?: return
        viewModelScope.launch {
            val saved = repository.saveWaveform(draft)
            _uiState.update { current ->
                current.copy(
                    selectedWaveformId = saved.id,
                    draftWaveform = saved,
                    isDirty = false,
                    editorMessage = "波形已保存",
                    isEditorVisible = true,
                )
            }
        }
    }

    fun requestDeleteSelectedWaveform() {
        val waveformId = _uiState.value.selectedWaveformId
        if (waveformId.isBlank()) {
            return
        }
        _uiState.update { current ->
            current.copy(pendingDeleteWaveformId = waveformId)
        }
    }

    fun dismissDeleteRequest() {
        _uiState.update { current ->
            current.copy(pendingDeleteWaveformId = null)
        }
    }

    fun confirmDeleteSelectedWaveform() {
        val repository = waveformLibraryRepository ?: return
        val waveformId = _uiState.value.pendingDeleteWaveformId ?: return
        viewModelScope.launch {
            runCatching {
                repository.deleteWaveform(waveformId)
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        pendingDeleteWaveformId = null,
                        editorMessage = "波形已删除",
                        isEditorVisible = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        pendingDeleteWaveformId = null,
                        editorMessage = error.message.orEmpty(),
                    )
                }
            }
        }
    }

    fun duplicateSelectedWaveform() {
        val repository = waveformLibraryRepository ?: return
        val selectedWaveformId = _uiState.value.selectedWaveformId
        if (selectedWaveformId.isBlank()) {
            return
        }
        viewModelScope.launch {
            val duplicated = repository.duplicateWaveform(selectedWaveformId)
            _uiState.update { current ->
                current.copy(
                    selectedWaveformId = duplicated.id,
                    draftWaveform = duplicated,
                    isDirty = false,
                    editorMessage = "已复制为自定义波形",
                    isEditorVisible = true,
                )
            }
        }
    }

    private fun syncUiState() {
        _uiState.update { current ->
            val selectedWaveform = when {
                current.selectedWaveformId.isBlank() -> currentWaveforms.firstOrNull()
                else -> currentWaveforms.firstOrNull { it.id == current.selectedWaveformId }
            } ?: currentWaveforms.firstOrNull()
            current.copy(
                waveforms = currentWaveforms,
                selectedWaveformId = selectedWaveform?.id.orEmpty(),
                draftWaveform = if (current.isDirty && current.draftWaveform?.id == selectedWaveform?.id) {
                    current.draftWaveform
                } else {
                    selectedWaveform
                },
            )
        }
    }

    private fun updateDraftStep(
        stepIndex: Int,
        transform: (com.yokonex.bililive.domain.model.WaveformStep) -> com.yokonex.bililive.domain.model.WaveformStep,
    ) {
        _uiState.update { current ->
            val draft = current.draftWaveform ?: return@update current
            if (stepIndex !in draft.steps.indices) {
                return@update current
            }
            val nextSteps = draft.steps.toMutableList()
            nextSteps[stepIndex] = transform(nextSteps[stepIndex])
            current.copy(
                draftWaveform = draft.copy(steps = nextSteps),
                isDirty = true,
            )
        }
    }

    private fun defaultStep() = com.yokonex.bililive.domain.model.WaveformStep(
        durationMs = 200,
        channelA = 0,
        channelB = 0,
    )
}

data class WaveformsUiState(
    val waveforms: List<WaveformDefinition> = emptyList(),
    val selectedWaveformId: String = "",
    val draftWaveform: WaveformDefinition? = null,
    val isDirty: Boolean = false,
    val editorMessage: String = "",
    val isEditorVisible: Boolean = false,
    val pendingSelectionWaveformId: String? = null,
    val pendingDeleteWaveformId: String? = null,
)

enum class WaveformChannel {
    A,
    B,
}
