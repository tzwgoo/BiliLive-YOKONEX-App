package com.yokonex.bililive.data.live

data class LiveRoomSessionInfo(
    val displayRoomId: String,
    val realRoomId: String,
    val ownerUid: Long,
    val token: String,
    val websocketHosts: List<String>,
)

interface LiveRoomClient : RoomProfileRepository {
    suspend fun createSessionInfo(roomId: String): LiveRoomSessionInfo
}

class DefaultBilibiliLiveRoomClient(
    private val api: BilibiliLiveApi = OkHttpBilibiliLiveApi(),
) : LiveRoomClient {
    override suspend fun createSessionInfo(roomId: String): LiveRoomSessionInfo {
        val playInfo = api.getRoomPlayInfo(roomId)
        val danmuInfo = api.getDanmuInfo(playInfo.realRoomId)
        return LiveRoomSessionInfo(
            displayRoomId = playInfo.displayRoomId,
            realRoomId = playInfo.realRoomId,
            ownerUid = playInfo.uid,
            token = danmuInfo.token,
            websocketHosts = danmuInfo.websocketHosts,
        )
    }

    override suspend fun getAnchorName(roomId: String): String? {
        if (roomId.isBlank()) {
            return null
        }
        val roomInfo = api.getRoomInfo(roomId)
        roomInfo.anchorName?.let { return it }
        val ownerUid = roomInfo.ownerUid
            ?: api.getRoomPlayInfo(roomId).uid.takeIf { it > 0L }
            ?: return null
        return api.getMasterInfo(ownerUid).uname?.trim()?.ifBlank { null }
    }
}
