package com.yokonex.bililive.data.live

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class BilibiliRoomPlayInfo(
    val displayRoomId: String,
    val realRoomId: String,
    val uid: Long,
)

data class BilibiliDanmuInfo(
    val token: String,
    val websocketHosts: List<String>,
)

data class BilibiliRoomInfo(
    val anchorName: String?,
    val ownerUid: Long?,
)

data class BilibiliMasterInfo(
    val uid: Long,
    val uname: String?,
)

interface BilibiliLiveApi {
    suspend fun getRoomPlayInfo(roomId: String): BilibiliRoomPlayInfo

    suspend fun getDanmuInfo(realRoomId: String): BilibiliDanmuInfo

    suspend fun getRoomInfo(roomId: String): BilibiliRoomInfo

    suspend fun getMasterInfo(uid: Long): BilibiliMasterInfo
}

class OkHttpBilibiliLiveApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val requestContext: BilibiliRequestContext = BilibiliRequestContext(),
) : BilibiliLiveApi {
    override suspend fun getRoomPlayInfo(roomId: String): BilibiliRoomPlayInfo = withContext(Dispatchers.IO) {
        val root = executeJsonRequest(
            url = "$XLIVE_ROOM_PLAY_INFO_URL?room_id=$roomId",
            roomId = roomId,
        ) ?: executeJsonRequest(
            url = "$LEGACY_ROOM_INIT_URL?id=$roomId",
            roomId = roomId,
        ) ?: JsonObject(emptyMap())
        val data = root["data"]?.jsonObject ?: JsonObject(emptyMap())
        val realRoomId = data["room_id"]?.jsonPrimitive?.contentOrNull?.ifBlank { roomId } ?: roomId
        val uid = data["uid"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        BilibiliRoomPlayInfo(
            displayRoomId = roomId,
            realRoomId = realRoomId,
            uid = uid,
        )
    }

    override suspend fun getDanmuInfo(realRoomId: String): BilibiliDanmuInfo = withContext(Dispatchers.IO) {
        val signedUrl = requestContext.buildSignedUrl(
            baseUrl = XLIVE_DANMU_INFO_URL,
            params = linkedMapOf(
                "id" to realRoomId,
                "type" to "0",
                "web_location" to "444.8",
            ),
            client = client,
            json = json,
        )
        val root = executeJsonRequest(
            url = signedUrl,
            roomId = realRoomId,
            includeBuvid = true,
        ) ?: executeJsonRequest(
            url = "$LEGACY_DANMU_INFO_URL?room_id=$realRoomId&platform=pc&player=web",
            roomId = realRoomId,
            includeBuvid = true,
        ) ?: JsonObject(emptyMap())
        val data = root["data"]?.jsonObject ?: JsonObject(emptyMap())
        val token = data["token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val hosts = sequenceOf("host_list", "host_server_list")
            .mapNotNull { key -> data[key] }
            .firstOrNull()
            ?.jsonArray
            ?.mapNotNull { element ->
                element.jsonObject["host"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            }
            .orEmpty()
        BilibiliDanmuInfo(
            token = token,
            websocketHosts = hosts,
        )
    }

    override suspend fun getRoomInfo(roomId: String): BilibiliRoomInfo = withContext(Dispatchers.IO) {
        val root = executeJsonRequest(
            url = "$XLIVE_ROOM_INFO_URL?room_id=$roomId",
            roomId = roomId,
            includeBuvid = true,
        ) ?: JsonObject(emptyMap())
        val data = root["data"]?.jsonObject ?: JsonObject(emptyMap())
        val anchorInfo = data["anchor_info"]?.jsonObject ?: JsonObject(emptyMap())
        val baseInfo = anchorInfo["base_info"]?.jsonObject ?: JsonObject(emptyMap())
        val roomInfo = data["room_info"]?.jsonObject ?: JsonObject(emptyMap())
        val anchorName = listOf(
            baseInfo["uname"]?.jsonPrimitive?.contentOrNull,
            anchorInfo["uname"]?.jsonPrimitive?.contentOrNull,
            roomInfo["uname"]?.jsonPrimitive?.contentOrNull,
            roomInfo["anchor_name"]?.jsonPrimitive?.contentOrNull,
        ).firstNotNullOfOrNull { candidate ->
            candidate?.trim()?.ifBlank { null }
        }
        val ownerUid = sequenceOf(
            baseInfo["uid"]?.jsonPrimitive?.contentOrNull,
            anchorInfo["uid"]?.jsonPrimitive?.contentOrNull,
            roomInfo["uid"]?.jsonPrimitive?.contentOrNull,
        ).mapNotNull { value ->
            value?.toLongOrNull()
        }.firstOrNull()
        BilibiliRoomInfo(
            anchorName = anchorName,
            ownerUid = ownerUid,
        )
    }

    override suspend fun getMasterInfo(uid: Long): BilibiliMasterInfo = withContext(Dispatchers.IO) {
        val root = executeJsonRequest(
            url = "$MASTER_INFO_URL?uid=$uid",
            roomId = uid.toString(),
            includeBuvid = true,
        ) ?: JsonObject(emptyMap())
        val info = root["data"]?.jsonObject?.get("info")?.jsonObject ?: JsonObject(emptyMap())
        BilibiliMasterInfo(
            uid = info["uid"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: uid,
            uname = info["uname"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
        )
    }

    private suspend fun executeJsonRequest(
        url: String,
        roomId: String,
        includeBuvid: Boolean = false,
    ): JsonObject? {
        val headers = requestContext.buildHeaders(
            roomId = roomId,
            includeBuvid = includeBuvid,
            client = client,
            json = json,
        )
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return null
            }
            return runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        }
    }

    private companion object {
        private const val XLIVE_ROOM_PLAY_INFO_URL =
            "https://api.live.bilibili.com/xlive/web-room/v1/index/getRoomPlayInfo"
        private const val XLIVE_DANMU_INFO_URL =
            "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo"
        private const val XLIVE_ROOM_INFO_URL =
            "https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom"
        private const val LEGACY_ROOM_INIT_URL =
            "https://api.live.bilibili.com/room/v1/Room/room_init"
        private const val LEGACY_DANMU_INFO_URL =
            "https://api.live.bilibili.com/room/v1/Danmu/getConf"
        private const val MASTER_INFO_URL =
            "https://api.live.bilibili.com/live_user/v1/Master/info"
    }
}

class BilibiliRequestContext {
    private var cachedBuvid3: String? = null
    private var cachedBuvid4: String? = null
    private var cachedMixinKey: String? = null

    suspend fun buildHeaders(
        roomId: String,
        includeBuvid: Boolean,
        client: OkHttpClient,
        json: Json,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val headers = linkedMapOf(
            "User-Agent" to DESKTOP_USER_AGENT,
            "Referer" to "https://live.bilibili.com/$roomId",
            "Accept" to "application/json, text/plain, */*",
        )
        if (includeBuvid) {
            ensureBuvid(client, json)
            val cookie = listOfNotNull(
                cachedBuvid3?.let { "buvid3=$it" },
                cachedBuvid4?.let { "buvid4=$it" },
            ).joinToString("; ")
            if (cookie.isNotBlank()) {
                headers["Cookie"] = cookie
            }
        }
        headers
    }

    suspend fun buildSignedUrl(
        baseUrl: String,
        params: LinkedHashMap<String, String>,
        client: OkHttpClient,
        json: Json,
    ): String = withContext(Dispatchers.IO) {
        val mixinKey = ensureMixinKey(client, json)
        val signedParams = params.toMutableMap()
        signedParams["wts"] = (System.currentTimeMillis() / 1000L).toString()
        if (signedParams["web_location"].isNullOrBlank()) {
            signedParams["web_location"] = "1550101"
        }
        val query = signedParams.toSortedMap()
            .entries
            .joinToString("&") { (key, value) ->
                "${urlEncode(key)}=${urlEncode(value)}"
            }
        val wRid = md5("$query$mixinKey")
        "$baseUrl?$query&w_rid=$wRid"
    }

    private fun ensureBuvid(client: OkHttpClient, json: Json) {
        if (!cachedBuvid3.isNullOrBlank() && !cachedBuvid4.isNullOrBlank()) {
            return
        }
        val request = Request.Builder()
            .url(SPI_URL)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://www.bilibili.com")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return
            }
            val body = response.body?.string().orEmpty()
            val data = runCatching {
                json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            }.getOrNull() ?: return
            cachedBuvid3 = data["b_3"]?.jsonPrimitive?.contentOrNull
            cachedBuvid4 = data["b_4"]?.jsonPrimitive?.contentOrNull
        }
    }

    private fun ensureMixinKey(client: OkHttpClient, json: Json): String {
        cachedMixinKey?.let { return it }
        val request = Request.Builder()
            .url(NAV_URL)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Referer", "https://www.bilibili.com")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return ""
            }
            val body = response.body?.string().orEmpty()
            val wbiImage = runCatching {
                json.parseToJsonElement(body)
                    .jsonObject["data"]
                    ?.jsonObject
                    ?.get("wbi_img")
                    ?.jsonObject
            }.getOrNull() ?: return ""
            val imgPart = wbiImage["img_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                .substringAfterLast("/")
                .substringBefore(".")
            val subPart = wbiImage["sub_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                .substringAfterLast("/")
                .substringBefore(".")
            val merged = imgPart + subPart
            cachedMixinKey = MIXIN_KEY_INDEX.fold(StringBuilder()) { builder, index ->
                if (index in merged.indices) {
                    builder.append(merged[index])
                }
                builder
            }.toString().take(32)
            return cachedMixinKey.orEmpty()
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        private const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
        private const val SPI_URL = "https://api.bilibili.com/x/frontend/finger/spi"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
        private val MIXIN_KEY_INDEX = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
        )
    }
}
