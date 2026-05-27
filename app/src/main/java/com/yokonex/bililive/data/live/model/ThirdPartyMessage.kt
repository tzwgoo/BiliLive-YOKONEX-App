package com.yokonex.bililive.data.live.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class ThirdPartyMessage(
    val cmd: String,
    val data: JsonObject = JsonObject(emptyMap()),
    val info: JsonArray? = null,
)
