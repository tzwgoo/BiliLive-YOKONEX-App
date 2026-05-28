package com.yokonex.bililive.data.storage

import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformExecutionMode
import com.yokonex.bililive.domain.model.WaveformStep
import kotlin.math.roundToInt

object DefaultWaveforms {
    private val presetNames = listOf(
        "呼吸",
        "潮汐",
        "连击",
        "快速按捏",
        "按捏渐强",
        "心跳节奏",
        "压缩",
        "节奏步伐",
        "颗粒摩擦",
        "渐变弹跳",
    )

    private val presetPatterns = listOf(
        listOf(140 to 6, 140 to 10, 140 to 14, 140 to 18, 140 to 24, 140 to 30, 220 to 0),
        listOf(120 to 6, 120 to 10, 120 to 14, 120 to 18, 120 to 22, 120 to 26, 120 to 22, 120 to 18, 120 to 14),
        listOf(70 to 28, 60 to 0, 70 to 28, 90 to 18, 180 to 0),
        listOf(120 to 30),
        listOf(90 to 8, 90 to 0, 90 to 14, 90 to 0, 90 to 20, 90 to 0, 90 to 26, 90 to 0, 90 to 30),
        listOf(90 to 30, 60 to 0, 60 to 18, 220 to 0, 90 to 26, 60 to 0, 60 to 14, 280 to 0),
        listOf(45 to 30, 20 to 0, 45 to 30, 28 to 0, 45 to 30, 36 to 0, 45 to 30, 44 to 0, 45 to 30, 52 to 0, 45 to 30, 60 to 0, 45 to 30, 68 to 0, 45 to 30),
        listOf(50 to 6, 80 to 0, 50 to 18, 70 to 0, 50 to 8, 60 to 0, 50 to 24, 50 to 0, 50 to 12, 40 to 0, 50 to 28, 30 to 0, 50 to 16),
        listOf(180 to 28, 180 to 28, 180 to 28, 180 to 28, 160 to 0),
        listOf(100 to 4, 110 to 0, 120 to 12, 90 to 0, 160 to 30),
    )

    val all: List<WaveformDefinition> = presetPatterns.mapIndexed { index, pattern ->
        val presetNumber = index + 1
        WaveformDefinition(
            id = "ems-preset-${presetNumber.toString().padStart(2, '0')}",
            name = "EMS 预设 ${presetNumber.toString().padStart(2, '0')} - ${presetNames[index]}",
            builtin = true,
            steps = pattern.map { (durationMs, strength) ->
                val mappedStrength = mapPresetStrength(strength)
                WaveformStep(
                    durationMs = durationMs,
                    channelA = mappedStrength,
                    channelAMode = presetNumber,
                    channelB = mappedStrength,
                    channelBMode = presetNumber,
                )
            },
            executionMode = WaveformExecutionMode.ONCE,
            loopCount = 1,
        )
    }

    private fun mapPresetStrength(strength: Int): Int {
        if (strength <= 0) {
            return 0
        }
        val clamped = strength.coerceIn(6, 30)
        val ratio = (clamped - 6).toFloat() / 24f
        return 40 + (ratio * 10f).roundToInt()
    }
}
