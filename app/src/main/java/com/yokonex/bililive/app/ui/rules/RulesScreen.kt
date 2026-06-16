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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.yokonex.bililive.domain.model.CooldownScope

@Composable
fun RulesScreen(
    uiState: RulesUiState,
    onRuleToggle: (String) -> Unit,
    onGiftPriceRangeChange: (String, String, String) -> Unit,
    onLikeMultipleChange: (String, String) -> Unit,
    onKeywordsChange: (String, String) -> Unit,
    onCooldownSecondsChange: (String, String) -> Unit,
    onCooldownScopeChange: (String, CooldownScope) -> Unit,
    onMinGuardLevelChange: (String, Int) -> Unit,
    onUserLimitWindowSecondsChange: (String, String) -> Unit,
    onUserLimitMaxTriggersChange: (String, String) -> Unit,
    onWaveformChange: (String, String) -> Unit,
    onGuardWaveformChange: (String, Int, String) -> Unit,
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
                    text = "当前页面已经支持按事件类型分别配置价格区间、点赞倍数、关键词、冷却范围、用户限流和舰队专属波形。",
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
                onLikeMultipleChange = onLikeMultipleChange,
                onKeywordsChange = onKeywordsChange,
                onCooldownSecondsChange = onCooldownSecondsChange,
                onCooldownScopeChange = onCooldownScopeChange,
                onMinGuardLevelChange = onMinGuardLevelChange,
                onUserLimitWindowSecondsChange = onUserLimitWindowSecondsChange,
                onUserLimitMaxTriggersChange = onUserLimitMaxTriggersChange,
                onWaveformChange = onWaveformChange,
                onGuardWaveformChange = onGuardWaveformChange,
            )
        }
    }
}

@Composable
private fun RuleEditorCard(
    rule: UiRuleItem,
    onRuleToggle: (String) -> Unit,
    onGiftPriceRangeChange: (String, String, String) -> Unit,
    onLikeMultipleChange: (String, String) -> Unit,
    onKeywordsChange: (String, String) -> Unit,
    onCooldownSecondsChange: (String, String) -> Unit,
    onCooldownScopeChange: (String, CooldownScope) -> Unit,
    onMinGuardLevelChange: (String, Int) -> Unit,
    onUserLimitWindowSecondsChange: (String, String) -> Unit,
    onUserLimitMaxTriggersChange: (String, String) -> Unit,
    onWaveformChange: (String, String) -> Unit,
    onGuardWaveformChange: (String, Int, String) -> Unit,
) {
    var minPriceText by rememberSaveable(rule.id, rule.minPriceText) { mutableStateOf(rule.minPriceText) }
    var maxPriceText by rememberSaveable(rule.id, rule.maxPriceText) { mutableStateOf(rule.maxPriceText) }
    var likeMultipleText by rememberSaveable(rule.id, rule.likeMultipleText) { mutableStateOf(rule.likeMultipleText) }
    var keywordsText by rememberSaveable(rule.id, rule.keywordsText) { mutableStateOf(rule.keywordsText) }
    var cooldownSecondsText by rememberSaveable(rule.id, rule.cooldownSecondsText) { mutableStateOf(rule.cooldownSecondsText) }
    var userLimitWindowText by rememberSaveable(rule.id, rule.userLimitWindowSecondsText) { mutableStateOf(rule.userLimitWindowSecondsText) }
    var userLimitMaxTriggersText by rememberSaveable(rule.id, rule.userLimitMaxTriggersText) { mutableStateOf(rule.userLimitMaxTriggersText) }

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
                        label = { Text("最低金额") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = maxPriceText,
                        onValueChange = { value ->
                            maxPriceText = value.filter(Char::isDigit)
                            onGiftPriceRangeChange(rule.id, minPriceText, maxPriceText)
                        },
                        label = { Text("最高金额") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
            if (rule.canEditLikeMultiple) {
                OutlinedTextField(
                    value = likeMultipleText,
                    onValueChange = { value ->
                        likeMultipleText = value.filter(Char::isDigit)
                        onLikeMultipleChange(rule.id, likeMultipleText)
                    },
                    label = { Text("点赞倍数") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            if (rule.canEditKeywords) {
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { value ->
                        keywordsText = value
                        onKeywordsChange(rule.id, keywordsText)
                    },
                    label = { Text("关键词（逗号分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (rule.canEditCooldownSeconds) {
                OutlinedTextField(
                    value = cooldownSecondsText,
                    onValueChange = { value ->
                        cooldownSecondsText = value.filter(Char::isDigit)
                        onCooldownSecondsChange(rule.id, cooldownSecondsText)
                    },
                    label = { Text("冷却秒数") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            if (rule.canEditUserLimitWindowSeconds || rule.canEditUserLimitMaxTriggers) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (rule.canEditUserLimitWindowSeconds) {
                        OutlinedTextField(
                            value = userLimitWindowText,
                            onValueChange = { value ->
                                userLimitWindowText = value.filter(Char::isDigit)
                                onUserLimitWindowSecondsChange(rule.id, userLimitWindowText)
                            },
                            label = { Text("用户限流窗口") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    if (rule.canEditUserLimitMaxTriggers) {
                        OutlinedTextField(
                            value = userLimitMaxTriggersText,
                            onValueChange = { value ->
                                userLimitMaxTriggersText = value.filter(Char::isDigit)
                                onUserLimitMaxTriggersChange(rule.id, userLimitMaxTriggersText)
                            },
                            label = { Text("窗口内最大次数") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }
            }
            if (rule.canEditCooldownScope) {
                CooldownScopeSelector(
                    ruleId = rule.id,
                    scope = rule.cooldownScope,
                    onCooldownScopeChange = onCooldownScopeChange,
                )
            }
            if (rule.canEditMinGuardLevel) {
                GuardLevelSelector(
                    ruleId = rule.id,
                    minGuardLevel = rule.minGuardLevel,
                    onMinGuardLevelChange = onMinGuardLevelChange,
                )
            }
            if (rule.canEditGuardWaveforms) {
                GuardWaveformSection(
                    rule = rule,
                    onGuardWaveformChange = onGuardWaveformChange,
                )
            }
        }
    }
}

@Composable
private fun GuardWaveformSection(
    rule: UiRuleItem,
    onGuardWaveformChange: (String, Int, String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "舰队专属波形覆盖",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "仅主礼物事件会按舰队等级优先选择这里的覆盖波形，留空则回退到规则主波形。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        rule.guardWaveforms.forEach { item ->
            GuardWaveformSelector(
                ruleId = rule.id,
                item = item,
                waveformOptions = rule.waveformOptions,
                onGuardWaveformChange = onGuardWaveformChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardWaveformSelector(
    ruleId: String,
    item: UiGuardWaveformItem,
    waveformOptions: List<UiWaveformOption>,
    onGuardWaveformChange: (String, Int, String) -> Unit,
) {
    var expanded by rememberSaveable(ruleId, "guard-waveform-${item.guardLevel}") { mutableStateOf(false) }
    val selectedLabel = waveformOptions
        .firstOrNull { option -> option.id == item.waveformId }
        ?.name
        ?: "跟随默认"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = { },
            readOnly = true,
            label = { Text("${item.label}波形") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("跟随默认") },
                onClick = {
                    expanded = false
                    onGuardWaveformChange(ruleId, item.guardLevel, "")
                },
            )
            waveformOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded = false
                        onGuardWaveformChange(ruleId, item.guardLevel, option.id)
                    },
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CooldownScopeSelector(
    ruleId: String,
    scope: CooldownScope,
    onCooldownScopeChange: (String, CooldownScope) -> Unit,
) {
    var expanded by rememberSaveable(ruleId, "cooldown-scope") { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = scope.toDisplayLabel(),
            onValueChange = { },
            readOnly = true,
            label = { Text("冷却范围") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            CooldownScope.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toDisplayLabel()) },
                    onClick = {
                        expanded = false
                        onCooldownScopeChange(ruleId, option)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardLevelSelector(
    ruleId: String,
    minGuardLevel: Int,
    onMinGuardLevelChange: (String, Int) -> Unit,
) {
    var expanded by rememberSaveable(ruleId, "guard-level") { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = minGuardLevel.toGuardLevelLabel(),
            onValueChange = { },
            readOnly = true,
            label = { Text("最低舰队等级") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            listOf(0, 3, 2, 1).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toGuardLevelLabel()) },
                    onClick = {
                        expanded = false
                        onMinGuardLevelChange(ruleId, option)
                    },
                )
            }
        }
    }
}

private fun CooldownScope.toDisplayLabel(): String =
    when (this) {
        CooldownScope.GLOBAL -> "全局冷却"
        CooldownScope.PER_USER -> "按用户冷却"
    }

private fun Int.toGuardLevelLabel(): String =
    when (this) {
        1 -> "总督及以上"
        2 -> "提督及以上"
        3 -> "舰长及以上"
        else -> "不限"
    }
