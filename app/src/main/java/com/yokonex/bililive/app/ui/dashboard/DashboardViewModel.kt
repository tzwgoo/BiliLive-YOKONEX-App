package com.yokonex.bililive.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.app.ui.components.UiEventLog
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.domain.usecase.StartMonitoringUseCase
import com.yokonex.bililive.domain.usecase.StopMonitoringUseCase
import com.yokonex.bililive.service.ServiceCoordinator
import com.yokonex.bililive.service.ServiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    private val serviceCoordinator = ServiceCoordinator()
    private val startMonitoringUseCase = StartMonitoringUseCase(serviceCoordinator)
    private val stopMonitoringUseCase = StopMonitoringUseCase(serviceCoordinator)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            serviceCoordinator.status.collect { status ->
                _uiState.update { currentState ->
                    currentState.copy(serviceStatus = status)
                }
            }
        }
    }

    fun toggleMonitoring() {
        viewModelScope.launch {
            if (_uiState.value.isMonitoring) {
                stopMonitoringUseCase()
            } else {
                startMonitoringUseCase()
            }
        }
    }

    fun selectOutputMode(mode: OutputMode) {
        _uiState.update { currentState ->
            currentState.copy(outputMode = mode)
        }
    }
}

data class DashboardUiState(
    val roomId: String = "22445566",
    val serviceStatus: ServiceStatus = ServiceStatus.Idle,
    val outputMode: OutputMode = OutputMode.BLUETOOTH,
    val recentEvents: List<UiEventLog> = sampleRecentEvents(),
) {
    val isMonitoring: Boolean
        get() = serviceStatus !is ServiceStatus.Idle && serviceStatus !is ServiceStatus.Stopping

    val serviceStatusLabel: String
        get() = when (serviceStatus) {
            ServiceStatus.Idle -> "待机"
            ServiceStatus.Starting -> "启动中"
            ServiceStatus.Running -> "监听中"
            ServiceStatus.Reconnecting -> "重连中"
            ServiceStatus.Stopping -> "停止中"
            is ServiceStatus.Error -> "异常"
        }

    val startButtonLabel: String
        get() = if (isMonitoring) "停止监听" else "启动监听"
}

private fun sampleRecentEvents(): List<UiEventLog> = listOf(
    UiEventLog(
        id = "evt_101",
        title = "礼物命中规则",
        detail = "用户 夏日汽水 送出 小心心 x10，已触发 dual_tap。",
        timestampLabel = "刚刚",
        statusLabel = "蓝牙已执行",
        success = true,
    ),
    UiEventLog(
        id = "evt_102",
        title = "点赞事件进入队列",
        detail = "用户 阿航 连点 30 次，等待下一轮波形调度。",
        timestampLabel = "1 分钟前",
        statusLabel = "等待输出",
        success = true,
    ),
    UiEventLog(
        id = "evt_103",
        title = "弹幕规则未命中",
        detail = "弹幕“晚上好”未匹配关键词，已记录为跳过。",
        timestampLabel = "3 分钟前",
        statusLabel = "未触发",
        success = false,
    ),
)
