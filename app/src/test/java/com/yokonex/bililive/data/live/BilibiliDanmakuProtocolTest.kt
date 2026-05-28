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

    @Test
    fun decodePackets_handlesBrotliPayload() {
        val outerPacket = BilibiliDanmakuProtocol.encodePacket(
            operation = BilibiliDanmakuProtocol.OP_SEND_SMS_REPLY,
            body = BROTLI_COMPRESSED_DANMAKU_PACKET,
            version = BilibiliDanmakuProtocol.PROTOCOL_VERSION_BROTLI,
        )

        val decodedPackets = BilibiliDanmakuProtocol.decodePackets(outerPacket)

        assertEquals(1, decodedPackets.size)
        assertEquals(BilibiliDanmakuProtocol.OP_SEND_SMS_REPLY, decodedPackets.first().operation)
        assertEquals(
            """{"cmd":"DANMU_MSG","info":[[0,0,0,0,1714113037],"测试弹幕",["1","测试用户"]]}""",
            decodedPackets.first().body.decodeToString(),
        )
    }

    private companion object {
        val BROTLI_COMPRESSED_DANMAKU_PACKET = byteArrayOf(
            11, 50, -128, 0, 0, 0, 101, 0, 16, 0, 1, 0, 0, 0, 5, 0,
            0, 0, 1, 123, 34, 99, 109, 100, 34, 58, 34, 68, 65, 78, 77, 85,
            95, 77, 83, 71, 34, 44, 34, 105, 110, 102, 111, 34, 58, 91, 91, 48,
            44, 48, 44, 48, 44, 48, 44, 49, 55, 49, 52, 49, 49, 51, 48, 51,
            55, 93, 44, 34, -26, -75, -117, -24, -81, -107, -27, -68, -71, -27, -71, -107,
            34, 44, 91, 34, 49, 34, 44, 34, -26, -75, -117, -24, -81, -107, -25, -108,
            -88, -26, -120, -73, 34, 93, 93, 125, 3,
        )
    }
}
