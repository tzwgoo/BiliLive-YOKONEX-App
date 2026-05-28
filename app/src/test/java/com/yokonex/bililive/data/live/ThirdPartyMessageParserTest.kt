package com.yokonex.bililive.data.live

import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.LiveEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class ThirdPartyMessageParserTest {
    @Test
    fun parse_danmakuVariantEvent_normalizesDanmuCmd() {
        val parser = ThirdPartyMessageParser(roomId = "22608112") { 1_714_113_037_000L }

        val event = parser.parse(
            """{"cmd":"DANMU_MSG:4:0:2:2:2:0","info":[[0,0,0,0,1714113037],"测试弹幕",["1001","测试用户"]]}""",
        )
        val payload = event.payload as EventPayload.DanmakuPayload

        assertEquals(LiveEventType.DANMAKU, event.type)
        assertEquals("1001", event.userId)
        assertEquals("测试用户", event.userName)
        assertEquals("测试弹幕", payload.message)
    }

    @Test
    fun parse_likeClickEvent_exposesLikeDelta() {
        val parser = ThirdPartyMessageParser(roomId = "22608112") { 1_714_113_037_000L }

        val event = parser.parse(
            """{"cmd":"LIKE_INFO_V3_CLICK","data":{"uname":"点赞用户","like_text":"点赞了直播间"}}""",
        )
        val payload = event.payload as EventPayload.LikePayload

        assertEquals(LiveEventType.LIKE, event.type)
        assertEquals(0, payload.likeCount)
        assertEquals(1, payload.likeDelta)
    }

    @Test
    fun parse_mappedGiftEvent_fromPythonRuntime_mapsToLiveEvent() {
        val parser = ThirdPartyMessageParser(roomId = "22608112") { 1_714_113_037_000L }

        val event = parser.parse(
            """{"source":"third_party_ws","event_type":"gift","cmd":"SEND_GIFT","room_id":22608112,"open_id":"","uname":"测试用户","timestamp":1714113037,"payload":{"gift_id":1,"gift_name":"辣条","gift_num":2,"price":100,"r_price":200}}""",
        )
        assertGiftEvent(
            event = event,
            expectedUserName = "测试用户",
            expectedGiftName = "辣条",
            expectedGiftNum = 2,
            expectedPrice = 100,
            expectedTotalPrice = 200,
        )
    }

    @Test
    fun parse_mappedComboGiftEvent_fromPythonRuntime_mapsToLiveEvent() {
        val parser = ThirdPartyMessageParser(roomId = "22608112") { 1_714_113_037_000L }

        val event = parser.parse(
            """{"source":"third_party_ws","event_type":"gift","cmd":"COMBO_SEND","room_id":22608112,"open_id":"","uname":"测试用户","timestamp":1714113037,"payload":{"gift_id":31039,"gift_name":"牛哇牛哇","gift_num":3,"price":100,"r_price":300}}""",
        )

        assertGiftEvent(
            event = event,
            expectedUserName = "测试用户",
            expectedGiftName = "牛哇牛哇",
            expectedGiftNum = 3,
            expectedPrice = 100,
            expectedTotalPrice = 300,
        )
    }

    @Test
    fun parse_mappedGuardBuyEvent_fromPythonRuntime_mapsToLiveEvent() {
        val parser = ThirdPartyMessageParser(roomId = "22608112") { 1_714_113_037_000L }

        val event = parser.parse(
            """{"source":"third_party_ws","event_type":"gift","cmd":"GUARD_BUY","room_id":22608112,"open_id":"","uname":"大航海用户","timestamp":1714113037,"payload":{"gift_id":1,"gift_name":"舰长","gift_num":2,"price":138000,"r_price":138000}}""",
        )

        assertGiftEvent(
            event = event,
            expectedUserName = "大航海用户",
            expectedGiftName = "舰长",
            expectedGiftNum = 2,
            expectedPrice = 138000,
            expectedTotalPrice = 138000,
        )
    }

    @Test
    fun parse_mappedSuperChatEvent_fromPythonRuntime_mapsToLiveEvent() {
        val parser = ThirdPartyMessageParser(roomId = "22608112") { 1_714_113_037_000L }

        val event = parser.parse(
            """{"source":"third_party_ws","event_type":"gift","cmd":"SUPER_CHAT_MESSAGE","room_id":22608112,"open_id":"","uname":"SC用户","timestamp":1714113037,"payload":{"gift_id":12000,"gift_name":"醒目留言","gift_num":1,"price":100,"r_price":100,"message":"测试 SC"}}""",
        )

        assertGiftEvent(
            event = event,
            expectedUserName = "SC用户",
            expectedGiftName = "醒目留言",
            expectedGiftNum = 1,
            expectedPrice = 100,
            expectedTotalPrice = 100,
        )
    }

    @Test
    fun parse_mappedUserToastEvent_fromPythonRuntime_mapsToLiveEvent() {
        val parser = ThirdPartyMessageParser(roomId = "22608112") { 1_714_113_037_000L }

        val event = parser.parse(
            """{"source":"third_party_ws","event_type":"gift","cmd":"USER_TOAST_MSG","room_id":22608112,"open_id":"","uname":"续费用户","timestamp":1714113037,"payload":{"gift_id":1,"gift_name":"舰长","gift_num":1,"price":50000,"r_price":50000,"toast_msg":"<%续费用户%>续费了舰长1个月"}}""",
        )

        assertGiftEvent(
            event = event,
            expectedUserName = "续费用户",
            expectedGiftName = "舰长",
            expectedGiftNum = 1,
            expectedPrice = 50000,
            expectedTotalPrice = 50000,
        )
    }

    @Test
    fun parse_multipleGiftEventsWithinSameMillisecond_generatesDistinctIds() {
        val parser = ThirdPartyMessageParser(roomId = "22608112") { 1_714_113_037_000L }

        val firstEvent = parser.parse(
            """{"source":"third_party_ws","event_type":"gift","cmd":"SEND_GIFT","room_id":22608112,"open_id":"","uname":"测试用户A","timestamp":1714113037,"payload":{"gift_id":1,"gift_name":"辣条","gift_num":1,"price":100,"r_price":100}}""",
        )
        val secondEvent = parser.parse(
            """{"source":"third_party_ws","event_type":"gift","cmd":"SEND_GIFT","room_id":22608112,"open_id":"","uname":"测试用户B","timestamp":1714113037,"payload":{"gift_id":2,"gift_name":"小花花","gift_num":1,"price":100,"r_price":100}}""",
        )

        assertEquals(false, firstEvent.id == secondEvent.id)
    }

    private fun assertGiftEvent(
        event: com.yokonex.bililive.domain.model.LiveEvent,
        expectedUserName: String,
        expectedGiftName: String,
        expectedGiftNum: Int,
        expectedPrice: Int,
        expectedTotalPrice: Int,
    ) {
        val payload = event.payload as EventPayload.GiftPayload

        assertEquals(LiveEventType.GIFT, event.type)
        assertEquals(expectedUserName, event.userName)
        assertEquals("22608112", event.roomId)
        assertEquals(expectedGiftName, payload.giftName)
        assertEquals(expectedGiftNum, payload.giftNum)
        assertEquals(expectedPrice, payload.price)
        assertEquals(expectedTotalPrice, payload.totalPrice)
    }
}
