package com.yokonex.bililive.app.ui.waveforms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WaveformsScreen(
    uiState: WaveformsUiState,
    onSelectWaveform: (String) -> Unit,
    onCreateWaveform: () -> Unit,
    onDuplicateSelectedWaveform: () -> Unit,
    onSaveDraft: () -> Unit,
    onWaveformNameChange: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "波形库",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "集中管理内置波形和自定义波形，后续会在这里接入拖拽编辑器。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Button(
                onClick = onCreateWaveform,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("新建空白波形")
            }
        }
        item {
            Text(
                text = "波形库列表",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(uiState.waveforms, key = { it.id }) { waveform ->
            Card(
                onClick = { onSelectWaveform(waveform.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = waveform.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (waveform.builtin) "内置波形" else "自定义波形",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Text(
                text = "波形编辑器",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            OutlinedTextField(
                value = uiState.draftWaveform?.name.orEmpty(),
                onValueChange = onWaveformNameChange,
                label = { Text("波形名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            Text(
                text = uiState.editorMessage.ifBlank { "选择一个波形后开始编辑。" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Button(
                onClick = onSaveDraft,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存波形")
            }
        }
        item {
            OutlinedButton(
                onClick = onDuplicateSelectedWaveform,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("复制为自定义")
            }
        }
    }
}
