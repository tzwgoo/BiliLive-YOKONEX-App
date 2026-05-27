package com.yokonex.bililive.domain.model

data class LiveEvent(
    val id: String,
    val type: LiveEventType,
    val timestamp: Long,
    val userId: String,
    val userName: String,
    val roomId: String,
    val payload: EventPayload,
)

enum class LiveEventType {
    GIFT,
    LIKE,
    DANMAKU,
    SYSTEM,
}

