package com.yokonex.bililive.data.waveform

import com.yokonex.bililive.domain.model.WaveformDefinition

interface WaveformLibraryRepository {
    suspend fun createWaveform(name: String = "自定义波形"): WaveformDefinition

    suspend fun duplicateWaveform(
        sourceWaveformId: String,
        name: String? = null,
    ): WaveformDefinition

    suspend fun saveWaveform(waveform: WaveformDefinition): WaveformDefinition

    suspend fun deleteWaveform(waveformId: String)
}
