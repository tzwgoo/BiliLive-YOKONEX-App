package com.yokonex.bililive.data.live

import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.LiveEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdPartyMessageParserTest {

    private val parser = ThirdPartyMessageParser(
        roomId = "2001",
        timestampProvider = { 123456789L },
    )

    @Test
    fun parser_mapsGiftMessageToLiveEvent() {
        val rawGiftJson = """
            {
              "cmd": "SEND_GIFT",
              "data": {
                "uid": "1001",
                "uname": "tester",
                "gift_name": "小电视",
                "num": 2,
                "price": 100
              }
            }
        """.trimIndent()

        val event = parser.parse(rawGiftJson)
        val payload = event.payload as EventPayload.GiftPayload

        assertEquals(LiveEventType.GIFT, event.type)
        assertEquals("1001", event.userId)
        assertEquals("tester", event.userName)
        assertEquals("2001", event.roomId)
        assertEquals("小电视", payload.giftName)
        assertEquals(2, payload.giftNum)
        assertEquals(100, payload.price)
        assertEquals(200, payload.totalPrice)
    }

    @Test
    fun parser_mapsLikeMessageToLiveEvent() {
        val rawLikeJson = """
            {
              "cmd": "LIKE_INFO_V3_CLICK",
              "data": {
                "uid": "1002",
                "uname": "点赞用户",
                "like_count": 20,
                "like_text": "点赞了"
              }
            }
        """.trimIndent()

        val event = parser.parse(rawLikeJson)
        val payload = event.payload as EventPayload.LikePayload

        assertEquals(LiveEventType.LIKE, event.type)
        assertEquals("1002", event.userId)
        assertEquals("点赞用户", event.userName)
        assertEquals(20, payload.likeCount)
        assertEquals("点赞了", payload.likeText)
    }

    @Test
    fun parser_mapsDanmakuMessageToLiveEvent() {
        val rawDanmakuJson = """
            {
              "cmd": "DANMU_MSG",
              "info": [
                [0, 0, 0, 0, 1714113037],
                "大家开始冲",
                ["1003", "弹幕用户"]
              ]
            }
        """.trimIndent()

        val event = parser.parse(rawDanmakuJson)
        val payload = event.payload as EventPayload.DanmakuPayload

        assertEquals(LiveEventType.DANMAKU, event.type)
        assertEquals("1003", event.userId)
        assertEquals("弹幕用户", event.userName)
        assertEquals("大家开始冲", payload.message)
        assertTrue(event.id.startsWith("DANMU_MSG-"))
    }

    @Test
    fun parser_mapsComboGiftMessageToLiveEvent() {
        val rawGiftJson = """
            {
              "cmd": "COMBO_SEND",
              "data": {
                "uid": "1004",
                "uname": "连击用户",
                "gift_name": "牛哇牛哇",
                "combo_num": 3,
                "price": 100,
                "combo_total_coin": 300
              }
            }
        """.trimIndent()

        val event = parser.parse(rawGiftJson)
        val payload = event.payload as EventPayload.GiftPayload

        assertEquals(LiveEventType.GIFT, event.type)
        assertEquals("1004", event.userId)
        assertEquals("连击用户", event.userName)
        assertEquals("牛哇牛哇", payload.giftName)
        assertEquals(3, payload.giftNum)
        assertEquals(100, payload.price)
        assertEquals(300, payload.totalPrice)
    }

    @Test
    fun parser_mapsLikeUpdateMessageToLiveEvent() {
        val rawLikeJson = """
            {
              "cmd": "LIKE_INFO_V3_UPDATE",
              "data": {
                "uname": "点赞用户",
                "click_count": 66
              }
            }
        """.trimIndent()

        val event = parser.parse(rawLikeJson)
        val payload = event.payload as EventPayload.LikePayload

        assertEquals(LiveEventType.LIKE, event.type)
        assertEquals("点赞用户", event.userName)
        assertEquals(66, payload.likeCount)
        assertEquals("点赞", payload.likeText)
    }
}
