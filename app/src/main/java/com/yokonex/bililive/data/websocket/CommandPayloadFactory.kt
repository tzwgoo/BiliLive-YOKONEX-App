package com.yokonex.bililive.data.websocket

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CommandPayloadFactory {
    fun buildLogin(
        uid: String,
        token: String,
    ): String = buildJsonObject {
        put("type", "login")
        put("uid", uid)
        put("token", token)
    }.toString()

    fun buildSendCommand(
        userId: String,
        commandSlot: String,
    ): String = buildJsonObject {
        put("type", "sendCommand")
        put("userId", userId)
        put("commandId", commandSlot)
    }.toString()

    fun buildLogout(userId: String): String = buildJsonObject {
        put("type", "logout")
        put("userId", userId)
    }.toString()

    fun buildPing(): String = buildJsonObject {
        put("type", "ping")
    }.toString()

    fun buildGetStatus(): String = buildJsonObject {
        put("type", "getStatus")
    }.toString()
}
