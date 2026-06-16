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
import java.util.concurrent.atomic.AtomicLong

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
    private val eventSequence = AtomicLong(0L)

    fun normalize(rawMessage: String): LiveEvent {
        val root = Json.parseToJsonElement(rawMessage).jsonObject
        if (root.containsKey("event_type") && root.containsKey("payload")) {
            return normalizeMappedEvent(root)
        }
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

    private fun normalizeMappedEvent(root: JsonObject): LiveEvent {
        val mappedRoomId = root.stringAny("room_id", "roomId").ifEmpty { roomId }
        val userId = root.stringAny("open_id", "uid")
        val userName = root.stringAny("uname", "user_name", "username")
        val timestamp = root.long("timestamp").takeIf { it > 0 } ?: timestampProvider()
        val cmd = normalizeCmd(root.string("cmd").ifEmpty { root.string("event_type") })
        val payload = root["payload"]?.jsonObject ?: JsonObject(emptyMap())
        val mappedEventType = root.string("event_type")

        return when (resolveMappedEventType(mappedEventType, cmd)) {
            LiveEventType.GIFT,
            LiveEventType.SUPER_CHAT,
            LiveEventType.GUARD_BUY,
            LiveEventType.GUARD_RENEW,
            -> mapMappedGiftFamilyEvent(
                cmd = cmd,
                eventType = resolveMappedEventType(mappedEventType, cmd),
                mappedRoomId = mappedRoomId,
                userId = userId,
                userName = userName,
                timestamp = timestamp,
                payload = payload,
            )

            LiveEventType.LIKE -> LiveEvent(
                id = nextEventId(cmd),
                type = LiveEventType.LIKE,
                timestamp = timestamp,
                userId = userId,
                userName = userName,
                roomId = mappedRoomId,
                payload = EventPayload.LikePayload(
                    likeCount = payload.intAny("like_count", "click_count", "count"),
                    likeText = payload.stringOrDefault("like_text", "点赞"),
                    likeDelta = payload.int("like_delta"),
                ),
            )

            LiveEventType.DANMAKU,
            LiveEventType.DANMAKU_CAPTAIN,
            LiveEventType.DANMAKU_COMMANDER,
            LiveEventType.DANMAKU_GOVERNOR,
            -> {
                val guardLevel = payload.intAny("guard_level", "guardLevel").coerceAtLeast(0)
                LiveEvent(
                    id = nextEventId(cmd),
                    type = resolveMappedEventType(mappedEventType, cmd, guardLevel),
                    timestamp = timestamp,
                    userId = userId,
                    userName = userName,
                    roomId = mappedRoomId,
                    payload = EventPayload.DanmakuPayload(
                        message = payload.stringAny("msg", "message"),
                        guardLevel = guardLevel,
                        guardLabel = payload.stringAny("guard_label", "guardLabel").ifBlank {
                            guardLevel.toGuardLabel()
                        },
                    ),
                )
            }

            LiveEventType.SYSTEM -> LiveEvent(
                id = nextEventId(cmd),
                type = LiveEventType.SYSTEM,
                timestamp = timestamp,
                userId = userId,
                userName = userName,
                roomId = mappedRoomId,
                payload = EventPayload.SystemPayload(message = cmd),
            )
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
        val guardLevel = data.resolveGiftGuardLevel()
        val eventType = resolveRawGiftEventType(message.cmd)
        return LiveEvent(
            id = nextEventId(message.cmd),
            type = eventType,
            timestamp = data.longAny("timestamp", "start_time", "ts").takeIf { it > 0 } ?: timestampProvider(),
            userId = data.string("uid"),
            userName = data.stringAny("uname", "username"),
            roomId = roomId,
            payload = EventPayload.GiftPayload(
                giftId = data.intAny("gift_id", "giftId"),
                giftName = data.stringAny("gift_name", "giftName", "role_name").ifEmpty {
                    eventType.displayLabel
                },
                giftNum = giftNum,
                price = price,
                totalPrice = totalPrice,
                message = data.string("message"),
                toastMessage = data.string("toast_msg"),
                guardLevel = guardLevel,
                guardLabel = resolveGuardLabel(
                    eventType = eventType,
                    explicitGuardLabel = data.stringAny("guard_label", "guardLabel"),
                    fallbackRoleName = data.stringAny("role_name", "gift_name", "giftName"),
                    guardLevel = guardLevel,
                ),
            ),
        )
    }

    private fun mapLike(message: ThirdPartyMessage): LiveEvent {
        val data = message.data
        val likeCount = data.intAny("like_count", "click_count", "count")
        return LiveEvent(
            id = nextEventId(message.cmd),
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
        val guardLevel = message.info.resolveDanmakuGuardLevel()
        return LiveEvent(
            id = nextEventId(message.cmd),
            type = guardLevel.toDanmakuEventType(),
            timestamp = infoHead.longAt(4).takeIf { it > 0 } ?: timestampProvider(),
            userId = userInfo.stringAt(0),
            userName = userInfo.stringAt(1),
            roomId = roomId,
            payload = EventPayload.DanmakuPayload(
                message = content,
                guardLevel = guardLevel,
                guardLabel = guardLevel.toGuardLabel(),
            ),
        )
    }

    private fun mapSystem(message: ThirdPartyMessage): LiveEvent =
        LiveEvent(
            id = nextEventId(message.cmd),
            type = LiveEventType.SYSTEM,
            timestamp = timestampProvider(),
            userId = "",
            userName = "",
            roomId = roomId,
            payload = EventPayload.SystemPayload(message = message.cmd),
        )

    private fun nextEventId(prefix: String): String =
        "$prefix-${timestampProvider()}-${eventSequence.getAndIncrement()}"

    // 这里统一兼容 Python 映射事件和原始 WS 事件，避免不同采集链路把 SC / 上舰语义压扁。
    private fun resolveMappedEventType(
        eventType: String,
        cmd: String,
        guardLevel: Int = 0,
    ): LiveEventType =
        when (eventType) {
            "gift" -> resolveRawGiftEventType(cmd)
            "super_chat" -> LiveEventType.SUPER_CHAT
            "guard_buy" -> LiveEventType.GUARD_BUY
            "guard_renew" -> LiveEventType.GUARD_RENEW
            "like" -> LiveEventType.LIKE
            "danmaku" -> guardLevel.toDanmakuEventType()
            "danmaku_captain" -> LiveEventType.DANMAKU_CAPTAIN
            "danmaku_commander" -> LiveEventType.DANMAKU_COMMANDER
            "danmaku_governor" -> LiveEventType.DANMAKU_GOVERNOR
            else -> LiveEventType.SYSTEM
        }

    private fun resolveRawGiftEventType(cmd: String): LiveEventType =
        when (cmd) {
            "SUPER_CHAT_MESSAGE",
            "SUPER_CHAT_MESSAGE_JPN",
            -> LiveEventType.SUPER_CHAT

            "GUARD_BUY" -> LiveEventType.GUARD_BUY
            "USER_TOAST_MSG" -> LiveEventType.GUARD_RENEW
            else -> LiveEventType.GIFT
        }

    private fun mapMappedGiftFamilyEvent(
        cmd: String,
        eventType: LiveEventType,
        mappedRoomId: String,
        userId: String,
        userName: String,
        timestamp: Long,
        payload: JsonObject,
    ): LiveEvent {
        val price = payload.int("price")
        val giftNum = payload.intAny("gift_num", "giftNum", "num").coerceAtLeast(1)
        val guardLevel = payload.intAny("guard_level", "guardLevel").coerceAtLeast(0)
        val totalPrice = payload.intAny("r_price", "total_coin", "combo_total_coin").takeIf { it > 0 }
            ?: price * giftNum
        return LiveEvent(
            id = nextEventId(cmd),
            type = eventType,
            timestamp = timestamp,
            userId = userId,
            userName = userName,
            roomId = mappedRoomId,
            payload = EventPayload.GiftPayload(
                giftId = payload.intAny("gift_id", "giftId"),
                giftName = payload.stringAny("gift_name", "giftName").ifEmpty { eventType.displayLabel },
                giftNum = giftNum,
                price = price,
                totalPrice = totalPrice,
                message = payload.string("message"),
                toastMessage = payload.string("toast_msg"),
                guardLevel = guardLevel,
                guardLabel = resolveGuardLabel(
                    eventType = eventType,
                    explicitGuardLabel = payload.stringAny("guard_label", "guardLabel"),
                    fallbackRoleName = payload.stringAny("gift_name", "giftName"),
                    guardLevel = guardLevel,
                ),
            ),
        )
    }

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

    private fun JsonObject.resolveGiftGuardLevel(): Int =
        intAny("guard_level", "guardLevel").takeIf { it > 0 }
            ?: runCatching {
                this["medal_info"]?.jsonObject?.intAny("guard_level", "guardLevel")
            }.getOrNull().takeIf { (it ?: 0) > 0 }
            ?: runCatching {
                this["uinfo"]?.jsonObject?.intAny("guard_level", "guardLevel")
            }.getOrNull().takeIf { (it ?: 0) > 0 }
            ?: 0

    private fun JsonArray?.resolveDanmakuGuardLevel(): Int {
        val directGuardLevel = longAt(7).toInt()
        if (directGuardLevel > 0) {
            return directGuardLevel
        }
        val medalInfo = arrayAt(3)
        val medalGuardLevel = medalInfo.longAt(10).toInt()
        if (medalGuardLevel > 0) {
            return medalGuardLevel
        }
        val nestedMedalGuardLevel = medalInfo?.getOrNull(0)
            ?.let { element -> runCatching { element.jsonObject }.getOrNull() }
            ?.intAny("guard_level", "guardLevel")
            ?: 0
        return nestedMedalGuardLevel.coerceAtLeast(0)
    }

    private fun Int.toDanmakuEventType(): LiveEventType =
        when (this) {
            3 -> LiveEventType.DANMAKU_CAPTAIN
            2 -> LiveEventType.DANMAKU_COMMANDER
            1 -> LiveEventType.DANMAKU_GOVERNOR
            else -> LiveEventType.DANMAKU
        }

    private fun Int.toGuardLabel(): String =
        when (this) {
            1 -> "总督"
            2 -> "提督"
            3 -> "舰长"
            else -> ""
        }

    private fun resolveGuardLabel(
        eventType: LiveEventType,
        explicitGuardLabel: String,
        fallbackRoleName: String,
        guardLevel: Int,
    ): String {
        if (explicitGuardLabel.isNotBlank()) {
            return explicitGuardLabel
        }
        if (eventType == LiveEventType.GUARD_BUY || eventType == LiveEventType.GUARD_RENEW) {
            return fallbackRoleName.ifBlank { guardLevel.toGuardLabel() }
        }
        return guardLevel.toGuardLabel()
    }
}
