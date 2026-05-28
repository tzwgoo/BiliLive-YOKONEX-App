package com.yokonex.bililive.data.live

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class PythonBridgeState {
    IDLE,
    CONNECTING,
    RUNNING,
    STOPPING,
    ERROR,
    ;

    companion object {
        fun fromRaw(value: String): PythonBridgeState =
            entries.firstOrNull { state -> state.name.equals(value, ignoreCase = true) } ?: IDLE
    }
}

data class PythonBridgeStatus(
    val state: PythonBridgeState,
    val lastError: String = "",
)

interface PythonThirdPartyBridge {
    fun start(roomId: String)

    fun drainEvents(limit: Int): List<String>

    fun getStatus(): PythonBridgeStatus

    fun stop()
}

class ChaquopyPythonThirdPartyBridge(
    private val context: Context,
) : PythonThirdPartyBridge {
    private val runtimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ensurePythonStarted(context)
        Python.getInstance()
            .getModule("live_runtime.third_party_runtime")
            .callAttr("create_runtime")
            ?: error("Python 第三方采集运行时初始化失败")
    }

    private val runtime: PyObject
        get() = runtimeDelegate.value

    override fun start(roomId: String) {
        runtime.callAttr("start", roomId)
    }

    override fun drainEvents(limit: Int): List<String> =
        runtime.callAttr("drain_events", limit)
            ?.asList()
            ?.map(PyObject::toString)
            .orEmpty()

    override fun getStatus(): PythonBridgeStatus {
        val rawJson = runtime.callAttr("get_status_json")?.toString().orEmpty()
        if (rawJson.isBlank()) {
            return PythonBridgeStatus(PythonBridgeState.IDLE)
        }
        val root = Json.parseToJsonElement(rawJson).jsonObject
        return PythonBridgeStatus(
            state = PythonBridgeState.fromRaw(root["state"]?.jsonPrimitive?.content.orEmpty()),
            lastError = root["last_error"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    override fun stop() {
        if (!runtimeDelegate.isInitialized()) {
            return
        }
        runtime.callAttr("stop")
    }

    private fun ensurePythonStarted(context: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
    }
}
