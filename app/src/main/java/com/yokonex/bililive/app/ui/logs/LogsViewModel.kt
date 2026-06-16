package com.yokonex.bililive.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.app.ui.components.UiEventLog
import com.yokonex.bililive.app.ui.dashboard.normalizeEventTimestampMillis
import com.yokonex.bililive.data.storage.JsonEventLogStore
import com.yokonex.bililive.data.storage.entity.EventLogEntity
import com.yokonex.bililive.domain.model.LiveEventCategory
import com.yokonex.bililive.domain.model.LiveEventType
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
    private var allLogs: List<EventLogEntity> = sampleLogEntities()

    init {
        eventLogStore?.let { store ->
            viewModelScope.launch {
                store.logs.collect { logs ->
                    allLogs = logs
                    _uiState.update { currentState ->
                        currentState.copy(
                            logs = filterLogs(logs, currentState.selectedFilter).map(::toUiEventLog),
                        )
                    }
                }
            }
        }
    }

    fun selectFilter(filter: LogEventFilter) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedFilter = filter,
                logs = filterLogs(allLogs, filter).map(::toUiEventLog),
            )
        }
    }
}

enum class LogEventFilter(val label: String) {
    ALL("全部"),
    GIFT("礼物"),
    LIKE("点赞"),
    DANMAKU("弹幕"),
    SYSTEM("系统"),
}

data class LogsUiState(
    val selectedFilter: LogEventFilter = LogEventFilter.ALL,
    val logs: List<UiEventLog> = sampleLogEntities().map(::toUiEventLog),
) {
    val availableFilters: List<LogEventFilter> = LogEventFilter.entries
}

internal fun filterLogs(
    logs: List<EventLogEntity>,
    filter: LogEventFilter,
): List<EventLogEntity> =
    when (filter) {
        LogEventFilter.ALL -> logs
        LogEventFilter.GIFT -> logs.filter { it.eventType.toLiveEventType()?.category == LiveEventCategory.GIFT }
        LogEventFilter.LIKE -> logs.filter { it.eventType.toLiveEventType()?.category == LiveEventCategory.LIKE }
        LogEventFilter.DANMAKU -> logs.filter { it.eventType.toLiveEventType()?.category == LiveEventCategory.DANMAKU }
        LogEventFilter.SYSTEM -> logs.filter { it.eventType.toLiveEventType()?.category == LiveEventCategory.SYSTEM }
    }

private fun sampleLogEntities(): List<EventLogEntity> = listOf(
    EventLogEntity(
        id = "log_201",
        eventType = "GIFT",
        summary = "dual_tap 已写入 YYC-DJ-V2-Alpha。",
        rawPayloadJson = "{}",
        matchedRuleId = "gift-tier-01",
        outputMode = "BLUETOOTH",
        outputSuccess = true,
        outputMessage = "ok",
        createdAt = 1_714_113_037_000L,
    ),
    EventLogEntity(
        id = "log_202",
        eventType = "SYSTEM",
        summary = "当前未建立 WebSocket 连接，指令未下发。",
        rawPayloadJson = "{}",
        matchedRuleId = null,
        outputMode = "WEBSOCKET",
        outputSuccess = false,
        outputMessage = "no_action_binding",
        createdAt = 1_714_113_027_000L,
    ),
    EventLogEntity(
        id = "log_203",
        eventType = "LIKE",
        summary = "事件已记录，等待下一次批量发送窗口。",
        rawPayloadJson = "{}",
        matchedRuleId = "like-default",
        outputMode = "BLUETOOTH",
        outputSuccess = true,
        outputMessage = "ok",
        createdAt = 1_714_113_017_000L,
    ),
)

internal fun toUiEventLog(entity: EventLogEntity): UiEventLog {
    val title = when (entity.eventType.toLiveEventType()) {
        LiveEventType.GIFT -> "礼物事件"
        LiveEventType.SUPER_CHAT -> "醒目留言事件"
        LiveEventType.GUARD_BUY -> "上舰事件"
        LiveEventType.GUARD_RENEW -> "续费事件"
        LiveEventType.LIKE -> "点赞事件"
        LiveEventType.DANMAKU -> "弹幕事件"
        LiveEventType.DANMAKU_CAPTAIN -> "舰长弹幕事件"
        LiveEventType.DANMAKU_COMMANDER -> "提督弹幕事件"
        LiveEventType.DANMAKU_GOVERNOR -> "总督弹幕事件"
        else -> "系统事件"
    }
    val statusLabel = when {
        entity.outputSuccess -> "成功"
        entity.outputMessage == "cooldown_skipped" -> "冷却跳过"
        entity.outputMessage == "user_limit_skipped" -> "限流跳过"
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
    return SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(normalizeEventTimestampMillis(timestamp)))
}

private fun String.toLiveEventType(): LiveEventType? =
    runCatching { LiveEventType.valueOf(this) }.getOrNull()
