package com.yokonex.bililive.data.live

interface RoomProfileRepository {
    suspend fun getAnchorName(roomId: String): String?
}

class BilibiliRoomProfileRepository(
    private val liveRoomClient: LiveRoomClient = DefaultBilibiliLiveRoomClient(),
) : RoomProfileRepository {
    override suspend fun getAnchorName(roomId: String): String? =
        liveRoomClient.getAnchorName(roomId)
}
