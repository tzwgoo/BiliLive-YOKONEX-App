package com.yokonex.bililive.data.live

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

object BilibiliDanmakuProtocol {
    const val HEADER_LENGTH = 16
    const val PROTOCOL_VERSION_RAW_JSON = 0
    const val PROTOCOL_VERSION_PLAIN = 1
    const val PROTOCOL_VERSION_ZLIB = 2
    const val SEQUENCE_ID = 1

    const val OP_HEARTBEAT = 2
    const val OP_HEARTBEAT_REPLY = 3
    const val OP_SEND_SMS_REPLY = 5
    const val OP_AUTH = 7
    const val OP_AUTH_REPLY = 8

    fun encodePacket(
        operation: Int,
        body: ByteArray = byteArrayOf(),
        version: Int = PROTOCOL_VERSION_PLAIN,
    ): ByteArray {
        val packetLength = HEADER_LENGTH + body.size
        val header = ByteBuffer.allocate(HEADER_LENGTH)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(packetLength)
            .putShort(HEADER_LENGTH.toShort())
            .putShort(version.toShort())
            .putInt(operation)
            .putInt(SEQUENCE_ID)
            .array()
        return header + body
    }

    fun decodePackets(data: ByteArray): List<DecodedPacket> {
        val packets = mutableListOf<DecodedPacket>()
        var offset = 0
        while (offset + HEADER_LENGTH <= data.size) {
            val header = ByteBuffer.wrap(data, offset, HEADER_LENGTH).order(ByteOrder.BIG_ENDIAN)
            val packetLength = header.int
            val headerLength = header.short.toInt()
            val version = header.short.toInt()
            val operation = header.int
            val sequenceId = header.int
            val bodyStart = offset + headerLength
            val bodyEnd = offset + packetLength
            if (bodyEnd > data.size || bodyStart > bodyEnd) {
                break
            }
            val body = data.copyOfRange(bodyStart, bodyEnd)
            offset = bodyEnd

            if (version == PROTOCOL_VERSION_ZLIB) {
                packets += decodePackets(inflate(body))
                continue
            }

            packets += DecodedPacket(
                packetLength = packetLength,
                headerLength = headerLength,
                version = version,
                operation = operation,
                sequenceId = sequenceId,
                body = body,
            )
        }
        return packets
    }

    fun readOperation(packet: ByteArray): Int =
        ByteBuffer.wrap(packet, 8, 4).order(ByteOrder.BIG_ENDIAN).int

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val buffer = ByteArray(4096)
        val output = ArrayList<Byte>()
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count <= 0 && inflater.needsInput()) {
                    break
                }
                repeat(count) { index ->
                    output += buffer[index]
                }
            }
        } finally {
            inflater.end()
        }
        return output.toByteArray()
    }
}

data class DecodedPacket(
    val packetLength: Int,
    val headerLength: Int,
    val version: Int,
    val operation: Int,
    val sequenceId: Int,
    val body: ByteArray,
)
