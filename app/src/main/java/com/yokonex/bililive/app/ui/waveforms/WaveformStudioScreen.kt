package com.yokonex.bililive.app.ui.waveforms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokonex.bililive.app.ui.components.StatusPill
import com.yokonex.bililive.app.ui.components.WorkspaceCard
import com.yokonex.bililive.app.ui.components.WorkspaceMetricCard
import com.yokonex.bililive.app.ui.components.WorkspacePageHeader
import com.yokonex.bililive.app.ui.components.workspaceFilledButtonColors
import com.yokonex.bililive.app.ui.components.workspaceOutlinedButtonColors
import com.yokonex.bililive.app.ui.components.workspaceOutlinedTextFieldColors
import com.yokonex.bililive.app.ui.output.OutputConfigUiState

@Composable
fun WaveformStudioScreen(
    uiState: WaveformsUiState,
    outputState: OutputConfigUiState,
    onSelectWaveform: (String) -> Unit,
    onCreateWaveform: () -> Unit,
    onCloseEditor: () -> Unit,
    onDuplicateSelectedWaveform: () -> Unit,
    onSaveDraft: () -> Unit,
    onWaveformNameChange: (String) -> Unit,
    onUpdateStepDuration: (Int, Int) -> Unit,
    onAppendStep: () -> Unit,
    onRemoveLastStep: () -> Unit,
    onDuplicateStep: (Int) -> Unit,
    onDeleteStep: (Int) -> Unit,
    onStrengthDrag: (Int, WaveformChannel, Int) -> Unit,
    onInsertStep: (Int) -> Unit,
    onRequestDeleteWaveform: () -> Unit,
    onDismissDeleteRequest: () -> Unit,
    onConfirmDeleteWaveform: () -> Unit,
    onConfirmPendingSelection: () -> Unit,
    onDismissPendingSelection: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val wide = maxWidth >= 1100.dp
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.width(360.dp),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        WaveformHero(
                            uiState = uiState,
                            outputState = outputState,
                            onCreateWaveform = onCreateWaveform,
                        )
                    }
                    items(uiState.waveforms, key = { it.id }) { waveform ->
                        WaveformListCard(
                            name = waveform.name,
                            builtin = waveform.builtin,
                            stepCount = waveform.steps.size,
                            selected = uiState.selectedWaveformId == waveform.id,
                            onClick = { onSelectWaveform(waveform.id) },
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        WaveformEditorWorkspace(
                            uiState = uiState,
                            onCloseEditor = onCloseEditor,
                            onDuplicateSelectedWaveform = onDuplicateSelectedWaveform,
                            onSaveDraft = onSaveDraft,
                            onWaveformNameChange = onWaveformNameChange,
                            onUpdateStepDuration = onUpdateStepDuration,
                            onAppendStep = onAppendStep,
                            onRemoveLastStep = onRemoveLastStep,
                            onDuplicateStep = onDuplicateStep,
                            onDeleteStep = onDeleteStep,
                            onStrengthDrag = onStrengthDrag,
                            onInsertStep = onInsertStep,
                            onRequestDeleteWaveform = onRequestDeleteWaveform,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    WaveformHero(
                        uiState = uiState,
                        outputState = outputState,
                        onCreateWaveform = onCreateWaveform,
                    )
                }
                items(uiState.waveforms, key = { it.id }) { waveform ->
                    WaveformListCard(
                        name = waveform.name,
                        builtin = waveform.builtin,
                        stepCount = waveform.steps.size,
                        selected = uiState.selectedWaveformId == waveform.id,
                        onClick = { onSelectWaveform(waveform.id) },
                    )
                }
                item {
                    WaveformEditorWorkspace(
                        uiState = uiState,
                        onCloseEditor = onCloseEditor,
                        onDuplicateSelectedWaveform = onDuplicateSelectedWaveform,
                        onSaveDraft = onSaveDraft,
                        onWaveformNameChange = onWaveformNameChange,
                        onUpdateStepDuration = onUpdateStepDuration,
                        onAppendStep = onAppendStep,
                        onRemoveLastStep = onRemoveLastStep,
                        onDuplicateStep = onDuplicateStep,
                        onDeleteStep = onDeleteStep,
                        onStrengthDrag = onStrengthDrag,
                        onInsertStep = onInsertStep,
                        onRequestDeleteWaveform = onRequestDeleteWaveform,
                    )
                }
            }
        }
    }

    if (uiState.pendingDeleteWaveformId != null) {
        AlertDialog(
            onDismissRequest = onDismissDeleteRequest,
            confirmButton = {
                TextButton(onClick = onConfirmDeleteWaveform) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteRequest) {
                    Text("取消")
                }
            },
            title = { Text("删除波形") },
            text = { Text("确认删除当前自定义波形吗？") },
        )
    }

    if (uiState.pendingSelectionWaveformId != null) {
        AlertDialog(
            onDismissRequest = onDismissPendingSelection,
            confirmButton = {
                TextButton(onClick = onConfirmPendingSelection) {
                    Text("放弃并切换")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPendingSelection) {
                    Text("继续编辑")
                }
            },
            title = { Text("未保存更改") },
            text = { Text("当前波形还有未保存更改，是否放弃修改并切换？") },
        )
    }
}

@Composable
private fun WaveformHero(
    uiState: WaveformsUiState,
    outputState: OutputConfigUiState,
    onCreateWaveform: () -> Unit,
) {
    WorkspaceCard {
        WorkspacePageHeader(title = "波形库")
        BoxWithConstraints {
            val compact = maxWidth < 560.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 顶部统计区在手机端改成纵向节奏，避免状态和指标互相挤压。
                    WorkspaceMetricCard(
                        label = "当前波形",
                        value = uiState.waveforms.size.toString(),
                    )
                    WorkspaceMetricCard(
                        label = "可编辑",
                        value = uiState.waveforms.count { !it.builtin }.toString(),
                    )
                    StatusPill(
                        label = if (outputState.canDisconnectBluetooth) "蓝牙已连接" else "蓝牙未连接",
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WorkspaceMetricCard(
                            label = "当前波形",
                            value = uiState.waveforms.size.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        WorkspaceMetricCard(
                            label = "可编辑",
                            value = uiState.waveforms.count { !it.builtin }.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    StatusPill(
                        label = if (outputState.canDisconnectBluetooth) "蓝牙已连接" else "蓝牙未连接",
                    )
                }
            }
        }
        Button(
            onClick = onCreateWaveform,
            modifier = Modifier.fillMaxWidth(),
            colors = workspaceFilledButtonColors(),
        ) {
            Text("新建空白波形")
        }
    }
}

@Composable
private fun WaveformListCard(
    name: String,
    builtin: Boolean,
    stepCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    WorkspaceCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (selected) {
                    StatusPill(label = "当前")
                }
            }
            Text(
                text = if (builtin) "内置波形" else "自定义波形",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "共 $stepCount 个步骤",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WaveformEditorWorkspace(
    uiState: WaveformsUiState,
    onCloseEditor: () -> Unit,
    onDuplicateSelectedWaveform: () -> Unit,
    onSaveDraft: () -> Unit,
    onWaveformNameChange: (String) -> Unit,
    onUpdateStepDuration: (Int, Int) -> Unit,
    onAppendStep: () -> Unit,
    onRemoveLastStep: () -> Unit,
    onDuplicateStep: (Int) -> Unit,
    onDeleteStep: (Int) -> Unit,
    onStrengthDrag: (Int, WaveformChannel, Int) -> Unit,
    onInsertStep: (Int) -> Unit,
    onRequestDeleteWaveform: () -> Unit,
) {
    val draft = uiState.draftWaveform
    val editable = draft?.builtin == false
    if (!uiState.isEditorVisible || draft == null) {
        WorkspaceCard {
            Text(
                text = "请先从波形列表选择一个波形进入编辑器。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    WorkspaceCard {
        BoxWithConstraints {
            val compact = maxWidth < 620.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onCloseEditor,
                        modifier = Modifier.fillMaxWidth(),
                        colors = workspaceOutlinedButtonColors(),
                    ) {
                        Text("返回列表")
                    }
                    OutlinedButton(
                        onClick = onDuplicateSelectedWaveform,
                        modifier = Modifier.fillMaxWidth(),
                        colors = workspaceOutlinedButtonColors(),
                    ) {
                        Text("复制为自定义")
                    }
                    Button(
                        onClick = onSaveDraft,
                        enabled = editable,
                        modifier = Modifier.fillMaxWidth(),
                        colors = workspaceFilledButtonColors(),
                    ) {
                        Text("保存波形")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onCloseEditor,
                        colors = workspaceOutlinedButtonColors(),
                    ) {
                        Text("返回列表")
                    }
                    OutlinedButton(
                        onClick = onDuplicateSelectedWaveform,
                        colors = workspaceOutlinedButtonColors(),
                    ) {
                        Text("复制为自定义")
                    }
                    Button(
                        onClick = onSaveDraft,
                        enabled = editable,
                        colors = workspaceFilledButtonColors(),
                    ) {
                        Text("保存波形")
                    }
                }
            }
            Text(
                text = uiState.editorMessage.ifBlank { "拖动画布上的控制点即可调整 A / B 通道强度。" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = onWaveformNameChange,
                label = { Text("波形名称") },
                modifier = Modifier.fillMaxWidth(),
                enabled = editable,
                singleLine = true,
                colors = workspaceOutlinedTextFieldColors(),
            )
            WaveformActionGroup(compact = compact) {
                Button(
                    onClick = onAppendStep,
                    enabled = editable,
                    colors = workspaceFilledButtonColors(),
                ) {
                    Text("新增步骤")
                }
                OutlinedButton(
                    onClick = onRemoveLastStep,
                    enabled = editable && draft.steps.size > 1,
                    colors = workspaceOutlinedButtonColors(),
                ) {
                    Text("减少步骤")
                }
                OutlinedButton(
                    onClick = onRequestDeleteWaveform,
                    enabled = editable,
                    colors = workspaceOutlinedButtonColors(),
                ) {
                    Text("删除波形")
                }
            }
            WaveformEditorCanvas(
                waveform = draft,
                editable = editable,
                onStrengthDrag = onStrengthDrag,
                onInsertStep = onInsertStep,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                draft.steps.forEachIndexed { index, step ->
                    WorkspaceCard {
                        Text(
                            text = "步骤 ${index + 1}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        OutlinedTextField(
                            value = step.durationMs.toString(),
                            onValueChange = { value ->
                                onUpdateStepDuration(index, value.filter(Char::isDigit).toIntOrNull() ?: 0)
                            },
                            label = { Text("时长 ms") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = editable,
                            singleLine = true,
                            colors = workspaceOutlinedTextFieldColors(),
                        )
                        Text(
                            text = "A ${step.channelA} / B ${step.channelB}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        WaveformActionGroup(compact = compact) {
                            OutlinedButton(
                                onClick = { onDuplicateStep(index) },
                                enabled = editable,
                                colors = workspaceOutlinedButtonColors(),
                            ) {
                                Text("复制当前步骤")
                            }
                            OutlinedButton(
                                onClick = { onDeleteStep(index) },
                                enabled = editable,
                                colors = workspaceOutlinedButtonColors(),
                            ) {
                                Text("删除当前步骤")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaveformActionGroup(
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    if (compact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = { content() },
        )
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = { content() },
        )
    }
}
