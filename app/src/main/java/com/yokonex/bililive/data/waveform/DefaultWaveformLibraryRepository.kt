package com.yokonex.bililive.data.waveform

import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.storage.JsonRuleStore
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformStep
import java.util.UUID

class DefaultWaveformLibraryRepository(
    private val waveformDao: WaveformDao,
    private val ruleStore: JsonRuleStore,
) : WaveformLibraryRepository {

    override suspend fun createWaveform(name: String): WaveformDefinition {
        val waveform = WaveformDefinition(
            id = generateWaveformId(),
            name = name.ifBlank { "自定义波形" },
            builtin = false,
            steps = listOf(defaultStep()),
        )
        waveformDao.upsert(WaveformMapper.toEntity(waveform))
        return waveform
    }

    override suspend fun duplicateWaveform(
        sourceWaveformId: String,
        name: String?,
    ): WaveformDefinition {
        val source = waveformDao.findById(sourceWaveformId)
            ?.let(WaveformMapper::fromEntity)
            ?: throw IllegalArgumentException("未找到波形: $sourceWaveformId")
        val duplicated = source.copy(
            id = generateWaveformId(),
            name = name?.ifBlank { null } ?: "${source.name} - 副本",
            builtin = false,
        )
        waveformDao.upsert(WaveformMapper.toEntity(duplicated))
        return duplicated
    }

    override suspend fun saveWaveform(waveform: WaveformDefinition): WaveformDefinition {
        if (waveform.builtin) {
            throw IllegalStateException("内置波形不支持直接编辑")
        }
        val normalized = waveform.copy(
            name = waveform.name.trim().ifBlank { throw IllegalStateException("波形名称不能为空") },
            steps = waveform.steps.ifEmpty { listOf(defaultStep()) }.map(::normalizeStep),
        )
        waveformDao.upsert(WaveformMapper.toEntity(normalized))
        return normalized
    }

    override suspend fun deleteWaveform(waveformId: String) {
        val waveform = waveformDao.findById(waveformId)
            ?.let(WaveformMapper::fromEntity)
            ?: throw IllegalArgumentException("未找到波形: $waveformId")
        if (waveform.builtin) {
            throw IllegalStateException("内置波形不支持删除")
        }
        if (ruleStore.rules.value.any { rule ->
                rule.actionBindings.bluetoothAction?.waveformId == waveformId
            }
        ) {
            throw IllegalStateException("请先修改规则绑定后再删除该波形")
        }
        waveformDao.deleteById(waveformId)
    }

    private fun normalizeStep(step: WaveformStep): WaveformStep =
        step.copy(
            durationMs = step.durationMs.coerceAtLeast(1),
            channelA = step.channelA.coerceIn(0, 180),
            channelB = step.channelB.coerceIn(0, 180),
        )

    private fun defaultStep(): WaveformStep =
        WaveformStep(
            durationMs = 200,
            channelA = 0,
            channelB = 0,
        )

    private fun generateWaveformId(): String =
        "custom-wave-${UUID.randomUUID().toString().replace("-", "").take(8)}"
}
