package com.yokonex.bililive.data.websocket

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandPayloadFactoryTest {

    private val factory = CommandPayloadFactory()

    @Test
    fun payloadFactory_buildsLoginMessage() {
        val payload = factory.buildLogin(
            uid = "game_123456",
            token = "token-demo",
        )

        assertEquals(
            """{"type":"login","uid":"game_123456","token":"token-demo"}""",
            payload,
        )
    }

    @Test
    fun payloadFactory_buildsSendCommandMessage() {
        val payload = factory.buildSendCommand(
            userId = "123456",
            commandSlot = "command_one",
        )

        assertEquals(
            """{"type":"sendCommand","userId":"123456","commandId":"command_one"}""",
            payload,
        )
    }
}
