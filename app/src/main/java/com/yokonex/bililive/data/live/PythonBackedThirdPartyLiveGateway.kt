package com.yokonex.bililive.data.live

import com.yokonex.bililive.domain.model.LiveEvent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive

class PythonBackedThirdPartyLiveGateway(
    private val bridge: PythonThirdPartyBridge,
    private val parserFactory: (String) -> ThirdPartyMessageParser = { roomId ->
        ThirdPartyMessageParser(roomId)
    },
    private val pollIntervalMillis: Long = 100L,
    private val batchSize: Int = 50,
) : ThirdPartyLiveGateway {
    override fun events(roomId: String): Flow<LiveEvent> = channelFlow {
        val parser = parserFactory(roomId)
        bridge.start(roomId)

        try {
            while (currentCoroutineContext().isActive) {
                val drainedEvents = bridge.drainEvents(batchSize)
                drainedEvents.forEach { rawEvent ->
                    send(parser.parse(rawEvent))
                }

                val status = bridge.getStatus()
                when (status.state) {
                    PythonBridgeState.ERROR -> {
                        throw IllegalStateException(
                            status.lastError.ifBlank { "Python 第三方采集失败" },
                        )
                    }

                    PythonBridgeState.IDLE -> {
                        if (drainedEvents.isEmpty()) {
                            throw IllegalStateException("Python 第三方采集已停止")
                        }
                    }

                    else -> Unit
                }

                delay(pollIntervalMillis)
            }
        } finally {
            bridge.stop()
        }
    }
}
