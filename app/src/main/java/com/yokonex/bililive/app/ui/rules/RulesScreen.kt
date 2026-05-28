package com.yokonex.bililive.app.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RulesScreen(
    uiState: RulesUiState,
    onRuleToggle: (String) -> Unit,
    onGiftPriceRangeChange: (String, String, String) -> Unit,
    onWaveformChange: (String, String) -> Unit,
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
                    text = "规则配置",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "蓝牙波形可切换，IM 槽位固定沿用原项目映射；礼物规则只开放单个礼物价值区间调整。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(uiState.rules, key = { it.id }) { rule ->
            RuleEditorCard(
                rule = rule,
                onRuleToggle = onRuleToggle,
                onGiftPriceRangeChange = onGiftPriceRangeChange,
                onWaveformChange = onWaveformChange,
            )
        }
    }
}

@Composable
private fun RuleEditorCard(
    rule: UiRuleItem,
    onRuleToggle: (String) -> Unit,
    onGiftPriceRangeChange: (String, String, String) -> Unit,
    onWaveformChange: (String, String) -> Unit,
) {
    var minPriceText by rememberSaveable(rule.id, rule.minPriceText) { mutableStateOf(rule.minPriceText) }
    var maxPriceText by rememberSaveable(rule.id, rule.maxPriceText) { mutableStateOf(rule.maxPriceText) }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onRuleToggle(rule.id) },
                )
            }
            Text(
                text = rule.summary,
                style = MaterialTheme.typography.bodyMedium,
            )
            WaveformSelector(
                rule = rule,
                onWaveformChange = onWaveformChange,
            )
            Text(
                text = rule.actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "IM ${rule.imSlotLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rule.canEditGiftPriceRange) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = minPriceText,
                        onValueChange = { value ->
                            minPriceText = value.filter(Char::isDigit)
                            onGiftPriceRangeChange(rule.id, minPriceText, maxPriceText)
                        },
                        label = { Text("最低单个礼物价值") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = maxPriceText,
                        onValueChange = { value ->
                            maxPriceText = value.filter(Char::isDigit)
                            onGiftPriceRangeChange(rule.id, minPriceText, maxPriceText)
                        },
                        label = { Text("最高单个礼物价值") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveformSelector(
    rule: UiRuleItem,
    onWaveformChange: (String, String) -> Unit,
) {
    var expanded by rememberSaveable(rule.id) { mutableStateOf(false) }
    val selectedLabel = rule.waveformOptions
        .firstOrNull { option -> option.id == rule.selectedWaveformId }
        ?.name
        .orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = { },
            readOnly = true,
            label = { Text("蓝牙波形") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            rule.waveformOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded = false
                        onWaveformChange(rule.id, option.id)
                    },
                )
            }
        }
    }
}
