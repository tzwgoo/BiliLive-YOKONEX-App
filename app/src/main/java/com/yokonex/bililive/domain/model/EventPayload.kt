package com.yokonex.bililive.domain.model

sealed interface EventPayload {
    data class GiftPayload(
        val giftName: String,
        val giftNum: Int,
        val price: Int,
        val totalPrice: Int,
    ) : EventPayload

    data class LikePayload(
        val likeCount: Int,
        val likeText: String,
    ) : EventPayload

    data class DanmakuPayload(
        val message: String,
    ) : EventPayload

    data class SystemPayload(
        val message: String,
    ) : EventPayload
}

