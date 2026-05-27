package com.yokonex.bililive.domain.model

data class WaveformDefinition(
    val id: String,
    val name: String,
    val builtin: Boolean,
    val steps: List<WaveformStep>,
    val executionMode: WaveformExecutionMode = WaveformExecutionMode.ONCE,
    val loopCount: Int = 1,
    val signalMode: WaveformSignalMode = WaveformSignalMode.FIXED,
)

data class WaveformStep(
    val durationMs: Int,
    val channelA: Int,
    val channelAMode: Int = 0x01,
    val channelAFrequency: Int = 10,
    val channelAPulseWidth: Int = 5,
    val channelB: Int,
    val channelBMode: Int = 0x01,
    val channelBFrequency: Int = 10,
    val channelBPulseWidth: Int = 5,
)

enum class WaveformExecutionMode {
    ONCE,
    LOOP,
}

enum class WaveformSignalMode {
    FIXED,
    REALTIME,
}
