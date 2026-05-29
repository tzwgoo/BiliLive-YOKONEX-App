package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.domain.model.LiveEventType

object MixPolicy {
    const val DEFAULT_TICK_MS: Long = 50L
    const val NORMAL_CAP: Int = 130
    const val GIFT_LEADER_CAP: Int = 180
    const val MAX_ACTIVE_LAYERS: Int = 4

    fun priorityOf(eventType: LiveEventType): Int =
        when (eventType) {
            LiveEventType.GIFT -> 3
            LiveEventType.DANMAKU -> 2
            LiveEventType.LIKE -> 1
            LiveEventType.SYSTEM -> 0
        }

    fun weightOf(eventType: LiveEventType): Double =
        when (eventType) {
            LiveEventType.GIFT -> 1.0
            LiveEventType.DANMAKU -> 0.4
            LiveEventType.LIKE -> 0.2
            LiveEventType.SYSTEM -> 0.0
        }
}
