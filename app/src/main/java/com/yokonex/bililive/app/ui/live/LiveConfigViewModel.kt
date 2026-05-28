package com.yokonex.bililive.app.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.data.storage.JsonRuleStore
import com.yokonex.bililive.data.storage.SettingsStore
import com.yokonex.bililive.domain.model.GiftTriggerMode
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.RuleConditions
import com.yokonex.bililive.domain.model.TriggerRule
import com.yokonex.bililive.domain.usecase.StartMonitoringUseCase
import com.yokonex.bililive.domain.usecase.StopMonitoringUseCase
import com.yokonex.bililive.service.LiveMonitorService
import com.yokonex.bililive.service.ServiceCoordinator
import com.yokonex.bililive.service.ServiceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LiveConfigViewModel(
    private val settingsStore: SettingsStore? = AppServices.container?.settingsStore,
    private val ruleStore: JsonRuleStore? = AppServices.container?.ruleStore,
    private val serviceCoordinator: ServiceCoordinator = AppServices.container?.serviceCoordinator ?: ServiceCoordinator(),
    private val batteryOptimizationStatusProvider: BatteryOptimizationStatusProvider =
        AppServices.applicationContext?.let(::AndroidBatteryOptimizationStatusProvider)
            ?: object : BatteryOptimizationStatusProvider {
                override fun currentStatus(): BatteryOptimizationStatus =
                    BatteryOptimizationStatus(
                        supported = false,
                        ignoringBatteryOptimizations = true,
                    )
            },
) : ViewModel() {
    private val startMonitoringUseCase = StartMonitoringUseCase(serviceCoordinator)
    private val stopMonitoringUseCase = StopMonitoringUseCase(serviceCoordinator)

    private val _uiState = MutableStateFlow(LiveConfigUiState())
    val uiState: StateFlow<LiveConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            serviceCoordinator.status.collect { status ->
                _uiState.update { currentState ->
                    currentState.copy(serviceStatus = status)
                }
            }
        }
        refreshBatteryOptimizationStatus()
        settingsStore?.let { store ->
            viewModelScope.launch {
                store.roomId.collect { roomId ->
                    _uiState.update { currentState ->
                        currentState.copy(roomId = roomId)
                    }
                }
            }
            viewModelScope.launch {
                store.reconnectIntervalSeconds.collect { seconds ->
                    _uiState.update { currentState ->
                        currentState.copy(reconnectIntervalSeconds = seconds.toString())
                    }
                }
            }
            viewModelScope.launch {
                store.autoReconnectEnabled.collect { enabled ->
                    _uiState.update { currentState ->
                        currentState.copy(autoReconnect = enabled)
                    }
                }
            }
            viewModelScope.launch {
                store.giftTriggerMode.collect { mode ->
                    _uiState.update { currentState ->
                        currentState.copy(giftTriggerMode = mode)
                    }
                }
            }
        }
        ruleStore?.let { store ->
            viewModelScope.launch {
                store.rules.collect { rules ->
                    val likeRule = rules.firstOrNull { it.id == LIKE_RULE_ID || it.eventType == LiveEventType.LIKE }
                    val danmakuRule = rules.firstOrNull { it.id == DANMAKU_RULE_ID || it.eventType == LiveEventType.DANMAKU }
                    _uiState.update { currentState ->
                        currentState.copy(
                            likeMultiple = likeRule?.conditions?.likeMultiple?.toString() ?: DEFAULT_LIKE_MULTIPLE.toString(),
                            danmakuEnabled = danmakuRule?.enabled ?: false,
                            danmakuKeywords = danmakuRule?.conditions?.keywords?.joinToString(",").orEmpty(),
                            danmakuCooldownSeconds = danmakuRule?.cooldownSeconds?.toString() ?: "0",
                        )
                    }
                }
            }
        }
    }

    fun updateRoomId(roomId: String) {
        val sanitized = roomId.filter(Char::isDigit).take(12)
        if (settingsStore == null) {
            _uiState.update { currentState ->
                currentState.copy(roomId = sanitized)
            }
            return
        }
        viewModelScope.launch {
            settingsStore.updateRoomId(sanitized)
        }
    }

    fun refreshBatteryOptimizationStatus() {
        val status = batteryOptimizationStatusProvider.currentStatus()
        _uiState.update { currentState ->
            currentState.copy(
                batteryOptimizationSupported = status.supported,
                batteryOptimizationIgnored = status.ignoringBatteryOptimizations,
            )
        }
    }

    fun toggleAutoReconnect(enabled: Boolean) {
        if (settingsStore == null) {
            _uiState.update { currentState ->
                currentState.copy(autoReconnect = enabled)
            }
            return
        }
        viewModelScope.launch {
            settingsStore.updateAutoReconnectEnabled(enabled)
        }
    }

    fun updateReconnectInterval(value: String) {
        val sanitized = value.filter(Char::isDigit).take(3)
        if (settingsStore == null) {
            _uiState.update { currentState ->
                currentState.copy(reconnectIntervalSeconds = sanitized)
            }
            return
        }
        _uiState.update { currentState ->
            currentState.copy(reconnectIntervalSeconds = sanitized)
        }
        viewModelScope.launch {
            settingsStore.updateReconnectIntervalSeconds(sanitized.toIntOrNull() ?: 3)
        }
    }

    fun updateGiftTriggerMode(mode: GiftTriggerMode) {
        if (settingsStore == null) {
            _uiState.update { currentState ->
                currentState.copy(giftTriggerMode = mode)
            }
            return
        }
        viewModelScope.launch {
            settingsStore.updateGiftTriggerMode(mode)
        }
    }

    fun updateLikeMultiple(value: String) {
        val sanitized = value.filter(Char::isDigit).take(4)
        _uiState.update { currentState ->
            currentState.copy(likeMultiple = sanitized)
        }
        if (ruleStore == null) {
            return
        }
        if (sanitized.isBlank()) {
            return
        }
        val normalized = sanitized.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_LIKE_MULTIPLE
        updateRule(LIKE_RULE_ID) { rule ->
            rule.copy(
                conditions = rule.conditions.copy(likeMultiple = normalized),
            )
        }
    }

    fun updateDanmakuEnabled(enabled: Boolean) {
        if (ruleStore == null) {
            _uiState.update { currentState ->
                currentState.copy(danmakuEnabled = enabled)
            }
            return
        }
        updateRule(DANMAKU_RULE_ID) { rule ->
            rule.copy(enabled = enabled)
        }
    }

    fun updateDanmakuKeywords(value: String) {
        val sanitized = value.trim()
        _uiState.update { currentState ->
            currentState.copy(danmakuKeywords = sanitized)
        }
        if (ruleStore == null) {
            return
        }
        updateRule(DANMAKU_RULE_ID) { rule ->
            rule.copy(
                conditions = rule.conditions.copy(
                    keywords = parseDanmakuKeywords(sanitized),
                ),
            )
        }
    }

    fun updateDanmakuCooldownSeconds(value: String) {
        val sanitized = value.filter(Char::isDigit).take(3)
        _uiState.update { currentState ->
            currentState.copy(danmakuCooldownSeconds = sanitized)
        }
        if (ruleStore == null) {
            return
        }
        if (sanitized.isBlank()) {
            return
        }
        updateRule(DANMAKU_RULE_ID) { rule ->
            rule.copy(
                cooldownSeconds = sanitized.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            )
        }
    }

    fun toggleMonitoring() {
        viewModelScope.launch {
            val appContext = AppServices.applicationContext
            if (appContext != null) {
                if (_uiState.value.isMonitoring) {
                    LiveMonitorService.stopService(appContext)
                } else {
                    LiveMonitorService.startService(
                        context = appContext,
                        roomId = _uiState.value.roomId,
                        outputMode = settingsStore?.outputMode?.first()
                            ?: com.yokonex.bililive.domain.model.OutputMode.BLUETOOTH,
                    )
                }
            } else {
                if (_uiState.value.isMonitoring) {
                    stopMonitoringUseCase()
                } else {
                    startMonitoringUseCase()
                }
            }
        }
    }

    private fun updateRule(
        ruleId: String,
        transform: (TriggerRule) -> TriggerRule,
    ) {
        val store = ruleStore ?: return
        val currentRules = store.rules.value
        val existingRule = currentRules.firstOrNull { it.id == ruleId } ?: defaultRule(ruleId)
        val updatedRule = transform(existingRule)
        viewModelScope.launch {
            store.updateRule(updatedRule)
        }
    }

    private fun defaultRule(ruleId: String): TriggerRule =
        when (ruleId) {
            LIKE_RULE_ID -> TriggerRule(
                id = LIKE_RULE_ID,
                name = "点赞默认规则",
                eventType = LiveEventType.LIKE,
                conditions = RuleConditions(likeMultiple = DEFAULT_LIKE_MULTIPLE),
            )

            DANMAKU_RULE_ID -> TriggerRule(
                id = DANMAKU_RULE_ID,
                name = "弹幕默认规则",
                enabled = false,
                eventType = LiveEventType.DANMAKU,
                conditions = RuleConditions(),
            )

            else -> error("未知规则 $ruleId")
        }
}

data class LiveConfigUiState(
    val roomId: String = "22445566",
    val autoReconnect: Boolean = true,
    val reconnectIntervalSeconds: String = "8",
    val giftTriggerMode: GiftTriggerMode = GiftTriggerMode.SINGLE,
    val likeMultiple: String = DEFAULT_LIKE_MULTIPLE.toString(),
    val danmakuEnabled: Boolean = false,
    val danmakuKeywords: String = "",
    val danmakuCooldownSeconds: String = "0",
    val providerName: String = "第三方直播消息流",
    val serviceStatus: ServiceStatus = ServiceStatus.Idle,
    val batteryOptimizationSupported: Boolean = false,
    val batteryOptimizationIgnored: Boolean = true,
) {
    val isMonitoring: Boolean
        get() = serviceStatus !is ServiceStatus.Idle && serviceStatus !is ServiceStatus.Stopping

    val monitoringStatus: String
        get() = when (serviceStatus) {
            ServiceStatus.Idle -> "待机"
            ServiceStatus.Starting -> "启动中"
            ServiceStatus.Running -> "监听中"
            ServiceStatus.Reconnecting -> "重连中"
            ServiceStatus.Stopping -> "停止中"
            is ServiceStatus.Error -> "异常"
        }

    val monitoringButtonLabel: String
        get() = if (isMonitoring) "停止监听" else "启动监听"

    val batteryOptimizationStatus: String
        get() = when {
            !batteryOptimizationSupported -> "系统未限制后台运行"
            batteryOptimizationIgnored -> "已关闭电池优化"
            else -> "建议关闭电池优化"
        }

    val batteryOptimizationHint: String
        get() = when {
            !batteryOptimizationSupported -> "当前系统版本通常不会因为 Doze 机制主动限制本应用的监听。"
            batteryOptimizationIgnored -> "系统已允许本应用在息屏后继续保持后台运行，监听会更稳定。"
            else -> "部分机型会在息屏后压后台网络或挂起协程，建议把本应用加入电池优化白名单。"
        }

    val shouldShowBatteryOptimizationAction: Boolean
        get() = batteryOptimizationSupported && !batteryOptimizationIgnored

    val giftTriggerModeLabel: String
        get() = when (giftTriggerMode) {
            GiftTriggerMode.SINGLE -> "单次触发"
            GiftTriggerMode.BY_QUANTITY -> "按数量触发"
        }
}

private const val LIKE_RULE_ID = "like-default"
private const val DANMAKU_RULE_ID = "danmaku-default"
private const val DEFAULT_LIKE_MULTIPLE = 100

internal fun parseDanmakuKeywords(value: String): List<String> =
    value
        .replace("\r", "")
        .replace("\n", ",")
        .split(",", "，")
        .map(String::trim)
        .filter(String::isNotBlank)
