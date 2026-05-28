package com.yokonex.bililive.data.live

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BilibiliLiveRoomClientTest {

    @Test
    fun createSessionInfo_fetchesPlayInfoBeforeDanmuInfo() = runTest {
        val api = FakeBilibiliLiveApi()
        val client = DefaultBilibiliLiveRoomClient(api)

        val sessionInfo = client.createSessionInfo("23121424")

        assertEquals("23121424", sessionInfo.displayRoomId)
        assertEquals("23121424", sessionInfo.realRoomId)
        assertEquals("token-demo", sessionInfo.token)
        assertEquals(listOf("playInfo:23121424", "danmuInfo:23121424"), api.calls)
    }

    @Test
    fun getAnchorName_fallsBackToMasterInfoWhenRoomInfoHasNoAnchorName() = runTest {
        val api = FakeBilibiliLiveApi(
            roomInfo = BilibiliRoomInfo(
                anchorName = null,
                ownerUid = 551188239L,
            ),
            masterInfo = BilibiliMasterInfo(
                uid = 551188239L,
                uname = "企鹅带带北极熊",
            ),
        )
        val client = DefaultBilibiliLiveRoomClient(api)

        val anchorName = client.getAnchorName("23121424")

        assertEquals("企鹅带带北极熊", anchorName)
        assertEquals(
            listOf("roomInfo:23121424", "masterInfo:551188239"),
            api.calls,
        )
    }
}

private class FakeBilibiliLiveApi(
    private val roomPlayInfo: BilibiliRoomPlayInfo = BilibiliRoomPlayInfo(
        displayRoomId = "23121424",
        realRoomId = "23121424",
        uid = 551188239L,
    ),
    private val danmuInfo: BilibiliDanmuInfo = BilibiliDanmuInfo(
        token = "token-demo",
        websocketHosts = listOf("broadcastlv.chat.bilibili.com"),
    ),
    private val roomInfo: BilibiliRoomInfo = BilibiliRoomInfo(
        anchorName = "企鹅带带北极熊",
        ownerUid = 551188239L,
    ),
    private val masterInfo: BilibiliMasterInfo = BilibiliMasterInfo(
        uid = 551188239L,
        uname = "企鹅带带北极熊",
    ),
) : BilibiliLiveApi {
    val calls = mutableListOf<String>()

    override suspend fun getRoomPlayInfo(roomId: String): BilibiliRoomPlayInfo {
        calls += "playInfo:$roomId"
        return roomPlayInfo
    }

    override suspend fun getDanmuInfo(realRoomId: String): BilibiliDanmuInfo {
        calls += "danmuInfo:$realRoomId"
        return danmuInfo
    }

    override suspend fun getRoomInfo(roomId: String): BilibiliRoomInfo {
        calls += "roomInfo:$roomId"
        return roomInfo
    }

    override suspend fun getMasterInfo(uid: Long): BilibiliMasterInfo {
        calls += "masterInfo:$uid"
        return masterInfo
    }
}
