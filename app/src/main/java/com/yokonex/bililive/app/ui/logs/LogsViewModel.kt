package com.yokonex.bililive.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.app.ui.components.UiEventLog
import com.yokonex.bililive.data.storage.JsonEventLogStore
import com.yokonex.bililive.data.storage.entity.EventLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LogsViewModel(
    private val eventLogStore: JsonEventLogStore? = AppServices.container?.eventLogStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        eventLogStore?.let { store ->
            viewModelScope.launch {
                store.logs.collect { logs ->
                    _uiState.update { currentState ->
                        currentState.copy(logs = logs.map(::toUiEventLog))
                    }
                }
            }
        }
    }
}

data class LogsUiState(
    val logs: List<UiEventLog> = listOf(
        UiEventLog(
            id = "log_201",
            title = "蓝牙执行成功",
            detail = "dual_tap 已写入 YYC-DJ-V2-Alpha。",
            timestampLabel = "刚刚",
            statusLabel = "成功",
            success = true,
        ),
        UiEventLog(
            id = "log_202",
            title = "Socket 输出跳过",
            detail = "当前未建立 WebSocket 连接，指令未下发。",
            timestampLabel = "2 分钟前",
            statusLabel = "跳过",
            success = false,
        ),
        UiEventLog(
            id = "log_203",
            title = "点赞规则进入队列",
            detail = "事件已记录，等待下一次批量发送窗口。",
            timestampLabel = "5 分钟前",
            statusLabel = "排队中",
            success = true,
        ),
    ),
)

private fun toUiEventLog(entity: EventLogEntity): UiEventLog {
    val title = when (entity.eventType) {
        "GIFT" -> "礼物事件"
        "LIKE" -> "点赞事件"
        "DANMAKU" -> "弹幕事件"
        else -> "系统事件"
    }
    val statusLabel = when {
        entity.outputSuccess -> "成功"
        entity.outputMessage == "no_matching_rule" -> "未命中"
        entity.outputMessage == "no_action_binding" -> "未绑定"
        else -> "失败"
    }
    return UiEventLog(
        id = entity.id,
        title = title,
        detail = entity.summary,
        timestampLabel = formatTimestamp(entity.createdAt),
        statusLabel = statusLabel,
        success = entity.outputSuccess,
    )
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "未知时间"
    }
    return SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(timestamp))
}
