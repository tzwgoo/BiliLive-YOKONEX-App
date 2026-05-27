package com.yokonex.bililive.data.live

import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Test

class BilibiliDanmakuProtocolTest {

    @Test
    fun encodePacket_setsExpectedOperation() {
        val packet = BilibiliDanmakuProtocol.encodePacket(
            operation = BilibiliDanmakuProtocol.OP_AUTH,
            body = """{"key":"value"}""".encodeToByteArray(),
        )

        assertEquals(BilibiliDanmakuProtocol.OP_AUTH, BilibiliDanmakuProtocol.readOperation(packet))
    }

    @Test
    fun decodePackets_handlesZlibPayload() {
        val innerPacket = BilibiliDanmakuProtocol.encodePacket(
            operation = BilibiliDanmakuProtocol.OP_SEND_SMS_REPLY,
            body = """{"cmd":"DANMU_MSG","info":[[0,0,0,0,1714113037],"测试弹幕",["1","测试用户"]]}""".encodeToByteArray(),
        )
        val compressedBody = Deflater().run {
            setInput(innerPacket, 0, innerPacket.size)
            finish()
            val buffer = ByteArray(512)
            val length = deflate(buffer)
            end()
            buffer.copyOf(length)
        }
        val outerPacket = BilibiliDanmakuProtocol.encodePacket(
            operation = BilibiliDanmakuProtocol.OP_SEND_SMS_REPLY,
            body = compressedBody,
            version = BilibiliDanmakuProtocol.PROTOCOL_VERSION_ZLIB,
        )

        val decodedPackets = BilibiliDanmakuProtocol.decodePackets(outerPacket)

        assertEquals(1, decodedPackets.size)
        assertEquals(BilibiliDanmakuProtocol.OP_SEND_SMS_REPLY, decodedPackets.first().operation)
        assertEquals(
            """{"cmd":"DANMU_MSG","info":[[0,0,0,0,1714113037],"测试弹幕",["1","测试用户"]]}""",
            decodedPackets.first().body.decodeToString(),
        )
    }
}
