package com.yokonex.bililive.domain.model

data class WaveformDefinition(
    val id: String,
    val name: String,
    val builtin: Boolean,
    val steps: List<WaveformStep>,
    val executionMode: WaveformExecutionMode = WaveformExecutionMode.ONCE,
    val loopCount: Int = 1,
)

data class WaveformStep(
    val durationMs: Int,
    val channelA: Int,
    val channelB: Int,
    val channelAMode: Int = 0x01,
    val channelAFrequency: Int = 10,
    val channelBMode: Int = 0x01,
    val channelBFrequency: Int = 10,
)

enum class WaveformExecutionMode {
    ONCE,
    LOOP,
}

