package com.yokonex.bililive.data.bluetooth.model

import com.yokonex.bililive.domain.model.LiveEventType

data class MixFrame(
    val channelA: Int,
    val channelB: Int,
    val channelAMode: Int,
    val channelAFrequency: Int,
    val channelAPulseWidth: Int,
    val channelBMode: Int,
    val channelBFrequency: Int,
    val channelBPulseWidth: Int,
    val cap: Int,
    val leaderEventType: LiveEventType?,
)
