package com.yokonex.bililive.data.bluetooth.model

import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformStep

data class ActiveWaveformLayer(
    val id: String,
    val eventType: LiveEventType,
    val waveform: WaveformDefinition,
    val startedAtElapsedMs: Long,
    val repeatCount: Int,
    val priority: Int,
    val weight: Double,
) {
    fun currentStepAt(nowElapsedMs: Long): WaveformStep? {
        val elapsed = (nowElapsedMs - startedAtElapsedMs).coerceAtLeast(0L)
        val steps = waveform.steps
        if (steps.isEmpty()) {
            return null
        }
        val totalDuration = steps.sumOf { step -> step.durationMs }.coerceAtLeast(1)
        val totalPlaybackDuration = totalDuration.toLong() * repeatCount.coerceAtLeast(1)
        if (elapsed >= totalPlaybackDuration) {
            return null
        }

        val offsetInLoop = (elapsed % totalDuration).toInt()
        var consumed = 0
        return steps.firstOrNull { step ->
            consumed += step.durationMs.coerceAtLeast(1)
            offsetInLoop < consumed
        } ?: steps.last()
    }
}
