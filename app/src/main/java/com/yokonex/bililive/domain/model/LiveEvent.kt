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

enum class LiveEventCategory {
    GIFT,
    LIKE,
    DANMAKU,
    SYSTEM,
}

enum class LiveEventType(
    val category: LiveEventCategory,
    val displayLabel: String,
) {
    GIFT(
        category = LiveEventCategory.GIFT,
        displayLabel = "礼物",
    ),
    SUPER_CHAT(
        category = LiveEventCategory.GIFT,
        displayLabel = "醒目留言",
    ),
    GUARD_BUY(
        category = LiveEventCategory.GIFT,
        displayLabel = "上舰",
    ),
    GUARD_RENEW(
        category = LiveEventCategory.GIFT,
        displayLabel = "续费",
    ),
    LIKE(
        category = LiveEventCategory.LIKE,
        displayLabel = "点赞",
    ),
    DANMAKU(
        category = LiveEventCategory.DANMAKU,
        displayLabel = "弹幕",
    ),
    DANMAKU_CAPTAIN(
        category = LiveEventCategory.DANMAKU,
        displayLabel = "舰长弹幕",
    ),
    DANMAKU_COMMANDER(
        category = LiveEventCategory.DANMAKU,
        displayLabel = "提督弹幕",
    ),
    DANMAKU_GOVERNOR(
        category = LiveEventCategory.DANMAKU,
        displayLabel = "总督弹幕",
    ),
    SYSTEM(
        category = LiveEventCategory.SYSTEM,
        displayLabel = "系统",
    ),
    ;

    val isGiftFamily: Boolean
        get() = category == LiveEventCategory.GIFT

    val isDanmakuFamily: Boolean
        get() = category == LiveEventCategory.DANMAKU

    val isLikeFamily: Boolean
        get() = category == LiveEventCategory.LIKE
}

