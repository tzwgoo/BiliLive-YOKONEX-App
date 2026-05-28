package com.yokonex.bililive.data.live

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomProfileRepositoryTest {

    @Test
    fun getAnchorName_delegatesToLiveRoomClient() = runTest {
        val repository = BilibiliRoomProfileRepository(
            liveRoomClient = object : LiveRoomClient {
                override suspend fun createSessionInfo(roomId: String): LiveRoomSessionInfo =
                    error("not used")

                override suspend fun getAnchorName(roomId: String): String? {
                    assertEquals("23121424", roomId)
                    return "企鹅带带北极熊"
                }
            },
        )

        val anchorName = repository.getAnchorName("23121424")

        assertEquals("企鹅带带北极熊", anchorName)
    }
}
