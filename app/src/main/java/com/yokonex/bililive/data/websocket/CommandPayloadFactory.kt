package com.yokonex.bililive.data.websocket

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CommandPayloadFactory {
    fun build(commandSlot: String): String = buildJsonObject {
        put("action", "sendCommand")
        put("commandId", commandSlot)
    }.toString()
}

