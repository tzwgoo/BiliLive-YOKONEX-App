package com.yokonex.bililive.data.live

import com.yokonex.bililive.data.live.model.ThirdPartyMessage
import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.LiveEventType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ThirdPartyMessageParser(
    private val roomId: String,
    private val timestampProvider: () -> Long = System::currentTimeMillis,
) {
    fun parse(rawMessage: String): LiveEvent {
        val root = Json.parseToJsonElement(rawMessage).jsonObject
        val message = ThirdPartyMessage(
            cmd = root.string("cmd"),
            data = root["data"]?.jsonObject ?: JsonObject(emptyMap()),
        )

        return when (message.cmd) {
            "SEND_GIFT" -> mapGift(message)
            "LIKE_INFO_V3_CLICK" -> mapLike(message)
            "DANMU_MSG" -> mapDanmaku(message)
            else -> mapSystem(message)
        }
    }

    private fun mapGift(message: ThirdPartyMessage): LiveEvent {
        val data = message.data
        val price = data.int("price")
        val giftNum = data.int("num")
        return LiveEvent(
            id = "${message.cmd}-${timestampProvider()}",
            type = LiveEventType.GIFT,
            timestamp = timestampProvider(),
            userId = data.string("uid"),
            userName = data.string("uname"),
            roomId = roomId,
            payload = EventPayload.GiftPayload(
                giftName = data.string("gift_name"),
                giftNum = giftNum,
                price = price,
                totalPrice = price * giftNum,
            ),
        )
    }

    private fun mapLike(message: ThirdPartyMessage): LiveEvent {
        val data = message.data
        return LiveEvent(
            id = "${message.cmd}-${timestampProvider()}",
            type = LiveEventType.LIKE,
            timestamp = timestampProvider(),
            userId = data.string("uid"),
            userName = data.string("uname"),
            roomId = roomId,
            payload = EventPayload.LikePayload(
                likeCount = data.int("like_count"),
                likeText = data.stringOrDefault("like_text", "点赞"),
            ),
        )
    }

    private fun mapDanmaku(message: ThirdPartyMessage): LiveEvent {
        val data = message.data
        return LiveEvent(
            id = "${message.cmd}-${timestampProvider()}",
            type = LiveEventType.DANMAKU,
            timestamp = timestampProvider(),
            userId = data.string("uid"),
            userName = data.string("uname"),
            roomId = roomId,
            payload = EventPayload.DanmakuPayload(
                message = data.string("message"),
            ),
        )
    }

    private fun mapSystem(message: ThirdPartyMessage): LiveEvent =
        LiveEvent(
            id = "${message.cmd}-${timestampProvider()}",
            type = LiveEventType.SYSTEM,
            timestamp = timestampProvider(),
            userId = "",
            userName = "",
            roomId = roomId,
            payload = EventPayload.SystemPayload(message = message.cmd),
        )

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.content ?: ""

    private fun JsonObject.stringOrDefault(
        key: String,
        fallback: String,
    ): String = this[key]?.jsonPrimitive?.content ?: fallback

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: 0
}
