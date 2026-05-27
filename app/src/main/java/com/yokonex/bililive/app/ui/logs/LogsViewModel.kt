package com.yokonex.bililive.app.ui.logs

import androidx.lifecycle.ViewModel
import com.yokonex.bililive.app.ui.components.UiEventLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LogsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()
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
