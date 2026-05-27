package com.yokonex.bililive.data.websocket

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandPayloadFactoryTest {

    private val factory = CommandPayloadFactory()

    @Test
    fun payloadFactory_buildsSendCommandMessage() {
        val payload = factory.build("command_one")

        assertEquals(
            """{"action":"sendCommand","commandId":"command_one"}""",
            payload,
        )
    }
}
