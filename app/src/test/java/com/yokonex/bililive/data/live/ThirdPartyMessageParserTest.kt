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
}
