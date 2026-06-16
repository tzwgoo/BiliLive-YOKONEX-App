package com.yokonex.bililive.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.app.ui.components.UiEventLog
import com.yokonex.bililive.data.bluetooth.BluetoothRepository
import com.yokonex.bililive.data.live.RoomProfileRepository
import com.yokonex.bililive.data.storage.JsonEventLogStore
import com.yokonex.bililive.data.storage.entity.EventLogEntity
import com.yokonex.bililive.data.storage.SettingsStore
import com.yokonex.bililive.data.websocket.CommandSocketClient
import com.yokonex.bililive.data.websocket.CommandSocketState
import com.yokonex.bililive.domain.model.LiveEventCategory
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.service.ServiceCoordinator
import com.yokonex.bililive.service.ServiceStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val serviceCoordinator: ServiceCoordinator = AppServices.container?.serviceCoordinator ?: ServiceCoordinator(),
    private val settingsStore: SettingsStore? = AppServices.container?.settingsStore,
    private val eventLogStore: JsonEventLogStore? = AppServices.container?.eventLogStore,
    private val bluetoothRepository: BluetoothRepository? = AppServices.container?.bluetoothRepository,
    private val commandSocketClient: CommandSocketClient? = AppServices.container?.commandSocketClient,
    private val roomProfileRepository: RoomProfileRepository? = AppServices.container?.roomProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var roomTitleJob: Job? = null

    init {
        viewModelScope.launch {
            serviceCoordinator.status.collect { status ->
                _uiState.update { currentState ->
                    currentState.copy(serviceStatus = status)
                }
            }
        }
        settingsStore?.let { store ->
            viewModelScope.launch {
                store.roomId.collect { roomId ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            roomId = roomId,
                            anchorName = if (roomId.isBlank()) {
                                DEFAULT_ANCHOR_NAME
                            } else {
                                ANCHOR_NAME_LOADING
                            },
                        )
                    }
                    refreshAnchorName(roomId)
                }
            }
            viewModelScope.launch {
                store.outputMode.collect { mode ->
                    _uiState.update { currentState ->
                        currentState.copy(outputMode = mode)
                    }
                }
            }
        }
        bluetoothRepository?.let { repository ->
            viewModelScope.launch {
                repository.runtimeStatus.collect { status ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            bluetoothConnected = status.connected,
                            bluetoothDeviceName = if (status.connected) status.deviceName else "",
                            bluetoothBatteryLevel = if (status.connected) status.batteryLevel else null,
                            channelAStrength = if (status.connected) status.channelAStrength else 0,
                            channelBStrength = if (status.connected) status.channelBStrength else 0,
                        )
                    }
                }
            }
        }
        commandSocketClient?.let { client ->
            viewModelScope.launch {
                client.connectionState.collect { state ->
                    _uiState.update { currentState ->
                        currentState.copy(imStatus = state.toDisplayLabel())
                    }
                }
            }
        }
        eventLogStore?.let { store ->
            viewModelScope.launch {
                store.logs.collect { logs ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            recentEventSections = buildDashboardEventSections(logs),
                        )
                    }
                }
            }
        }
    }

    private fun refreshAnchorName(roomId: String) {
        roomTitleJob?.cancel()
        if (roomId.isBlank()) {
            _uiState.update { currentState ->
                currentState.copy(anchorName = DEFAULT_ANCHOR_NAME)
            }
            return
        }
        val repository = roomProfileRepository ?: run {
            _uiState.update { currentState ->
                currentState.copy(anchorName = DEFAULT_ANCHOR_NAME)
            }
            return
        }
        roomTitleJob = viewModelScope.launch {
            val resolvedName = runCatching { repository.getAnchorName(roomId) }.getOrNull()
            _uiState.update { currentState ->
                currentState.copy(anchorName = normalizeAnchorName(resolvedName))
            }
        }
    }
}

data class DashboardUiState(
    val roomId: String = "22445566",
    val anchorName: String = DEFAULT_ANCHOR_NAME,
    val serviceStatus: ServiceStatus = ServiceStatus.Running,
    val outputMode: OutputMode = OutputMode.BLUETOOTH,
    val bluetoothConnected: Boolean = false,
    val bluetoothDeviceName: String = "",
    val bluetoothBatteryLevel: Int? = null,
    val channelAStrength: Int = 0,
    val channelBStrength: Int = 0,
    val imStatus: String = "未连接",
    val recentEventSections: List<DashboardEventSection> = sampleDashboardEventSections(),
) {
    val serviceStatusLabel: String
        get() = when (serviceStatus) {
            ServiceStatus.Idle -> "待机"
            ServiceStatus.Starting -> "启动中"
            ServiceStatus.Running -> "监听中"
            ServiceStatus.Reconnecting -> "重连中"
            ServiceStatus.Stopping -> "停止中"
            is ServiceStatus.Error -> "异常"
        }

    val serviceSupportingText: String
        get() = when (val status = serviceStatus) {
            is ServiceStatus.Error -> "房间 $roomId\n错误：${status.message}"
            else -> "房间 $roomId"
        }

    val outputModeLabel: String
        get() = if (outputMode == OutputMode.BLUETOOTH) "蓝牙 EMS" else "IM 指令"
}

data class DashboardEventSection(
    val title: String,
    val supportingText: String,
    val events: List<UiEventLog>,
)

private fun sampleDashboardEventSections(): List<DashboardEventSection> = listOf(
    DashboardEventSection(
        title = "礼物",
        supportingText = "最近 1 条礼物",
        events = listOf(
            UiEventLog(
                id = "evt_101",
                title = "实时礼物",
                detail = "用户 夏日汽水 送出 小心心 x10。",
                timestampLabel = "刚刚",
                statusLabel = "已触发",
                success = true,
            ),
        ),
    ),
    DashboardEventSection(
        title = "点赞",
        supportingText = "最近 2 条点赞",
        events = listOf(
            UiEventLog(
                id = "evt_102",
                title = "实时点赞",
                detail = "用户 阿航 点赞 30。",
                timestampLabel = "1 分钟前",
                statusLabel = "未命中",
                success = false,
            ),
            UiEventLog(
                id = "evt_104",
                title = "实时点赞",
                detail = "用户 阿宁 点赞 120。",
                timestampLabel = "4 分钟前",
                statusLabel = "已触发",
                success = true,
            ),
        ),
    ),
    DashboardEventSection(
        title = "弹幕",
        supportingText = "最近 1 条弹幕",
        events = listOf(
            UiEventLog(
                id = "evt_103",
                title = "实时弹幕",
                detail = "用户 晚风 发送弹幕 晚上好。",
                timestampLabel = "3 分钟前",
                statusLabel = "冷却跳过",
                success = false,
            ),
        ),
    ),
)

internal fun toDashboardEventLog(entity: EventLogEntity): UiEventLog =
    UiEventLog(
        id = entity.id,
        title = dashboardEventTitle(entity.eventType),
        detail = entity.summary,
        timestampLabel = formatDashboardTimestamp(entity.createdAt),
        statusLabel = dashboardTriggerStatus(entity),
        success = entity.outputSuccess,
    )

internal fun normalizeAnchorName(name: String?): String =
    name?.trim()?.takeIf(String::isNotEmpty) ?: DEFAULT_ANCHOR_NAME

internal fun normalizeEventTimestampMillis(timestamp: Long): Long =
    if (timestamp in 1L until 1_000_000_000_000L) {
        timestamp * 1_000L
    } else {
        timestamp
    }

internal fun buildDashboardRecentEvents(logs: List<EventLogEntity>): List<UiEventLog> {
    val liveLogs = logs
        .filter { log ->
            log.eventType.toLiveEventType()
                ?.let { eventType -> eventType.category != LiveEventCategory.SYSTEM }
                ?: false
        }
        .sortedByDescending(EventLogEntity::createdAt)
    if (liveLogs.size <= DASHBOARD_RECENT_EVENT_LIMIT) {
        return liveLogs.map(::toDashboardEventLog)
    }

    val pinnedIds = buildSet {
        PRIORITIZED_EVENT_CATEGORIES.forEach { eventCategory ->
            liveLogs.firstOrNull { log -> log.eventType.toLiveEventType()?.category == eventCategory }?.let { log ->
                add(log.id)
            }
        }
    }

    val prioritized = liveLogs.filter { log -> log.id in pinnedIds }
    val remainder = liveLogs.filterNot { log -> log.id in pinnedIds }
        .take(DASHBOARD_RECENT_EVENT_LIMIT - prioritized.size)

    return (prioritized + remainder)
        .sortedByDescending(EventLogEntity::createdAt)
        .take(DASHBOARD_RECENT_EVENT_LIMIT)
        .map(::toDashboardEventLog)
}

internal fun buildDashboardEventSections(logs: List<EventLogEntity>): List<DashboardEventSection> =
    DASHBOARD_SECTION_CATEGORIES.map { eventCategory ->
        val title = eventCategory.toSectionTitle()
        val typedLogs = logs
            .filter { log -> log.eventType.toLiveEventType()?.category == eventCategory }
            .sortedByDescending(EventLogEntity::createdAt)
        DashboardEventSection(
            title = title,
            supportingText = "最近 ${min(typedLogs.size, DASHBOARD_SECTION_EVENT_LIMIT)} 条$title",
            events = typedLogs
                .take(DASHBOARD_SECTION_EVENT_LIMIT)
                .map(::toDashboardEventLog),
        )
    }

private fun dashboardEventTitle(eventType: String): String =
    when (eventType.toLiveEventType()) {
        LiveEventType.GIFT -> "实时礼物"
        LiveEventType.SUPER_CHAT -> "实时醒目留言"
        LiveEventType.GUARD_BUY -> "实时上舰"
        LiveEventType.GUARD_RENEW -> "实时续费"
        LiveEventType.LIKE -> "实时点赞"
        LiveEventType.DANMAKU -> "实时弹幕"
        LiveEventType.DANMAKU_CAPTAIN -> "实时舰长弹幕"
        LiveEventType.DANMAKU_COMMANDER -> "实时提督弹幕"
        LiveEventType.DANMAKU_GOVERNOR -> "实时总督弹幕"
        else -> "系统事件"
    }

private fun dashboardTriggerStatus(entity: EventLogEntity): String =
    when {
        entity.outputSuccess -> "已触发"
        entity.outputMessage == "cooldown_skipped" -> "冷却跳过"
        entity.outputMessage == "user_limit_skipped" -> "限流跳过"
        entity.outputMessage == "no_matching_rule" -> "未命中"
        entity.outputMessage == "no_action_binding" -> "未绑定"
        else -> "输出失败"
    }

private fun formatDashboardTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "未知时间"
    }
    return SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(normalizeEventTimestampMillis(timestamp)))
}

private fun CommandSocketState.toDisplayLabel(): String =
    when (this) {
        CommandSocketState.DISCONNECTED -> "未连接"
        CommandSocketState.CONNECTING -> "连接中"
        CommandSocketState.CONNECTED -> "已连接"
        CommandSocketState.ERROR -> "连接异常"
    }

private fun String.toLiveEventType(): LiveEventType? =
    runCatching { LiveEventType.valueOf(this) }.getOrNull()

private fun LiveEventCategory.toSectionTitle(): String =
    when (this) {
        LiveEventCategory.GIFT -> "礼物"
        LiveEventCategory.LIKE -> "点赞"
        LiveEventCategory.DANMAKU -> "弹幕"
        LiveEventCategory.SYSTEM -> "系统"
    }

private val DASHBOARD_SECTION_CATEGORIES = listOf(
    LiveEventCategory.GIFT,
    LiveEventCategory.LIKE,
    LiveEventCategory.DANMAKU,
)
private val PRIORITIZED_EVENT_CATEGORIES = listOf(
    LiveEventCategory.GIFT,
    LiveEventCategory.LIKE,
    LiveEventCategory.DANMAKU,
)
private const val DASHBOARD_RECENT_EVENT_LIMIT = 10
private const val DASHBOARD_SECTION_EVENT_LIMIT = 4
private const val DEFAULT_ANCHOR_NAME = "未获取主播名称"
private const val ANCHOR_NAME_LOADING = "主播名称获取中"
