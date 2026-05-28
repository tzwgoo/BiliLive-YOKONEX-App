package com.yokonex.bililive.data.live

import com.yokonex.bililive.data.live.model.ThirdPartyMessage
import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.LiveEventType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ThirdPartyMessageParser(
    private val roomId: String,
    private val timestampProvider: () -> Long = System::currentTimeMillis,
) {
    private val normalizer = BilibiliDanmakuEventNormalizer(roomId, timestampProvider)

    fun parse(rawMessage: String): LiveEvent {
        return normalizer.normalize(rawMessage)
    }
}

class BilibiliDanmakuEventNormalizer(
    private val roomId: String,
    private val timestampProvider: () -> Long = System::currentTimeMillis,
) {
    fun normalize(rawMessage: String): LiveEvent {
        val root = Json.parseToJsonElement(rawMessage).jsonObject
        val message = ThirdPartyMessage(
            cmd = normalizeCmd(root.string("cmd")),
            data = root["data"]?.jsonObject ?: JsonObject(emptyMap()),
            info = root["info"]?.jsonArray,
        )

        return when (message.cmd) {
            "SEND_GIFT",
            "COMBO_SEND",
            "GUARD_BUY",
            "SUPER_CHAT_MESSAGE",
            "SUPER_CHAT_MESSAGE_JPN",
            "USER_TOAST_MSG",
            -> mapGift(message)

            "LIKE_INFO_V3_CLICK",
            "LIKE_INFO_V3_UPDATE",
            -> mapLike(message)

            "DANMU_MSG" -> mapDanmaku(message)
            else -> mapSystem(message)
        }
    }

    private fun normalizeCmd(cmd: String): String =
        when {
            cmd.contains("RECALL_DANMU_MSG") -> "RECALL_DANMU_MSG"
            cmd.contains("DANMU_MSG") -> "DANMU_MSG"
            else -> cmd
        }

    private fun mapGift(message: ThirdPartyMessage): LiveEvent {
        val data = message.data
        val price = data.int("price")
        val giftNum = data.intAny("combo_num", "num", "gift_num").coerceAtLeast(1)
        val totalPrice = data.intAny("combo_total_coin", "total_coin", "r_price").takeIf { it > 0 }
            ?: price * giftNum
        return LiveEvent(
            id = "${message.cmd}-${timestampProvider()}",
            type = LiveEventType.GIFT,
            timestamp = data.longAny("timestamp", "start_time", "ts").takeIf { it > 0 } ?: timestampProvider(),
            userId = data.string("uid"),
            userName = data.stringAny("uname", "username"),
            roomId = roomId,
            payload = EventPayload.GiftPayload(
                giftName = data.stringAny("gift_name", "giftName", "role_name").ifEmpty { "礼物" },
                giftNum = giftNum,
                price = price,
                totalPrice = totalPrice,
            ),
        )
    }

    private fun mapLike(message: ThirdPartyMessage): LiveEvent {
        val data = message.data
        val likeCount = data.intAny("like_count", "click_count", "count")
        return LiveEvent(
            id = "${message.cmd}-${timestampProvider()}",
            type = LiveEventType.LIKE,
            timestamp = data.long("timestamp").takeIf { it > 0 } ?: timestampProvider(),
            userId = data.string("uid"),
            userName = data.string("uname"),
            roomId = roomId,
            payload = EventPayload.LikePayload(
                likeCount = likeCount,
                likeText = data.stringOrDefault("like_text", "点赞"),
                likeDelta = if (message.cmd == "LIKE_INFO_V3_CLICK" && likeCount <= 0) 1 else 0,
            ),
        )
    }

    private fun mapDanmaku(message: ThirdPartyMessage): LiveEvent {
        val content = message.info.stringAt(1)
        val userInfo = message.info.arrayAt(2)
        val infoHead = message.info.arrayAt(0)
        return LiveEvent(
            id = "${message.cmd}-${timestampProvider()}",
            type = LiveEventType.DANMAKU,
            timestamp = infoHead.longAt(4).takeIf { it > 0 } ?: timestampProvider(),
            userId = userInfo.stringAt(0),
            userName = userInfo.stringAt(1),
            roomId = roomId,
            payload = EventPayload.DanmakuPayload(
                message = content,
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

    private fun JsonObject.stringAny(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        } ?: ""

    private fun JsonObject.stringOrDefault(
        key: String,
        fallback: String,
    ): String = this[key]?.jsonPrimitive?.content ?: fallback

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: 0

    private fun JsonObject.intAny(vararg keys: String): Int =
        keys.firstNotNullOfOrNull { key ->
            this[key]?.jsonPrimitive?.intOrNull
        } ?: 0

    private fun JsonObject.long(key: String): Long =
        this[key]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

    private fun JsonObject.longAny(vararg keys: String): Long =
        keys.firstNotNullOfOrNull { key ->
            this[key]?.jsonPrimitive?.content?.toLongOrNull()
        } ?: 0L

    private fun JsonArray?.stringAt(index: Int): String =
        this?.getOrNull(index)?.jsonPrimitive?.content ?: ""

    private fun JsonArray?.longAt(index: Int): Long =
        this?.getOrNull(index)?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

    private fun JsonArray?.arrayAt(index: Int): JsonArray? =
        this?.getOrNull(index)?.let { element ->
            runCatching { element.jsonArray }.getOrNull()
        }
}
