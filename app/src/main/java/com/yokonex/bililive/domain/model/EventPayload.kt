package com.yokonex.bililive.domain.model

sealed interface EventPayload {
    data class GiftPayload(
        val giftId: Int = 0,
        val giftName: String,
        val giftNum: Int,
        val price: Int,
        val totalPrice: Int,
        val message: String = "",
        val toastMessage: String = "",
        val guardLevel: Int = 0,
        val guardLabel: String = "",
    ) : EventPayload

    data class LikePayload(
        val likeCount: Int,
        val likeText: String,
        val likeDelta: Int = 0,
    ) : EventPayload

    data class DanmakuPayload(
        val message: String,
        val guardLevel: Int = 0,
        val guardLabel: String = "",
    ) : EventPayload

    data class SystemPayload(
        val message: String,
    ) : EventPayload
}
