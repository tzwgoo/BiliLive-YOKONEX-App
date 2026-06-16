package com.yokonex.bililive

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yokonex.bililive.data.bluetooth.AndroidBleManager
import com.yokonex.bililive.data.bluetooth.BluetoothRepository
import com.yokonex.bililive.data.bluetooth.DefaultBluetoothRepository
import com.yokonex.bililive.data.bluetooth.EmsProtocolEncoder
import com.yokonex.bililive.data.bluetooth.EmsWaveformRuntime
import com.yokonex.bililive.data.bluetooth.PlatformAndroidBleManager
import com.yokonex.bililive.data.live.DefaultBilibiliLiveRoomClient
import com.yokonex.bililive.data.live.RealThirdPartyLiveGateway
import com.yokonex.bililive.data.live.BilibiliRoomProfileRepository
import com.yokonex.bililive.data.live.LiveRoomClient
import com.yokonex.bililive.data.live.PythonBackedThirdPartyLiveGateway
import com.yokonex.bililive.data.live.ChaquopyPythonThirdPartyBridge
import com.yokonex.bililive.data.live.RoomProfileRepository
import com.yokonex.bililive.data.live.ThirdPartyLiveGateway
import com.yokonex.bililive.data.mapper.RuleMapper
import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.storage.DefaultWaveforms
import com.yokonex.bililive.data.storage.JsonEventLogStore
import com.yokonex.bililive.data.storage.JsonRuleStore
import com.yokonex.bililive.data.storage.JsonWaveformDao
import com.yokonex.bililive.data.storage.SettingsStore
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.data.waveform.DefaultWaveformLibraryRepository
import com.yokonex.bililive.data.waveform.WaveformLibraryRepository
import com.yokonex.bililive.data.websocket.CommandSocketClient
import com.yokonex.bililive.data.websocket.OkHttpCommandSocketClient
import com.yokonex.bililive.domain.model.ActionBindings
import com.yokonex.bililive.domain.model.CooldownScope
import com.yokonex.bililive.domain.model.GiftTriggerMode
import com.yokonex.bililive.domain.model.KeywordMatchMode
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.domain.model.RuleConditions
import com.yokonex.bililive.domain.model.TriggerRule
import com.yokonex.bililive.domain.usecase.OutputModeProvider
import com.yokonex.bililive.domain.usecase.ProcessLiveEventUseCase
import com.yokonex.bililive.domain.usecase.GiftTriggerModeProvider
import com.yokonex.bililive.service.MonitoringConfig
import com.yokonex.bililive.service.MonitoringConfigProvider
import com.yokonex.bililive.service.ServiceCoordinator
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(
    context: Context,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore = PreferenceDataStoreFactory.create(
        scope = appScope,
        produceFile = {
            File(context.filesDir, "settings.preferences_pb")
        },
    )
    val settingsStore = SettingsStore(dataStore)

    val waveformDao: WaveformDao = JsonWaveformDao(
        file = File(context.filesDir, "storage/waveforms.json"),
        defaultWaveforms = DefaultWaveforms.all.map(WaveformMapper::toEntity),
    )
    val ruleStore = JsonRuleStore(
        file = File(context.filesDir, "storage/rules.json"),
        defaultRules = buildDefaultRules(),
    )
    val eventLogStore = JsonEventLogStore(
        file = File(context.filesDir, "storage/event_logs.json"),
    )
    val waveformLibraryRepository: WaveformLibraryRepository = DefaultWaveformLibraryRepository(
        waveformDao = waveformDao,
        ruleStore = ruleStore,
    )
    private val liveRoomClient: LiveRoomClient = DefaultBilibiliLiveRoomClient()
    val roomProfileRepository: RoomProfileRepository = BilibiliRoomProfileRepository(liveRoomClient)
    private val bleManager: AndroidBleManager = PlatformAndroidBleManager(context.applicationContext)
    private val protocolEncoder = EmsProtocolEncoder()
    val commandSocketClient: CommandSocketClient = OkHttpCommandSocketClient()
    val bluetoothRepository: BluetoothRepository = DefaultBluetoothRepository(
        bleManager = bleManager,
        waveformDao = waveformDao,
        settingsStore = settingsStore,
        waveformRuntime = EmsWaveformRuntime(bleManager, protocolEncoder),
        protocolEncoder = protocolEncoder,
    )
    private val processLiveEventUseCase = ProcessLiveEventUseCase(
        ruleRepository = ruleStore,
        outputModeProvider = SettingsOutputModeProvider(settingsStore),
        giftTriggerModeProvider = SettingsGiftTriggerModeProvider(settingsStore),
        bluetoothRepository = bluetoothRepository,
        commandSocketClient = commandSocketClient,
        eventLogRepository = eventLogStore,
    )

    val serviceCoordinator = ServiceCoordinator(
        configProvider = SettingsMonitoringConfigProvider(settingsStore),
        liveGateway = createThirdPartyLiveGateway(context, liveRoomClient),
        commandSocketClient = commandSocketClient,
        eventProcessor = processLiveEventUseCase::invoke,
    )

    init {
        appScope.launch {
            settingsStore.ensureDefaults()
        }
    }
}

private fun createThirdPartyLiveGateway(
    context: Context,
    liveRoomClient: LiveRoomClient,
): ThirdPartyLiveGateway =
    if (BuildConfig.DEBUG) {
        PythonBackedThirdPartyLiveGateway(
            bridge = ChaquopyPythonThirdPartyBridge(context.applicationContext),
        )
    } else {
        RealThirdPartyLiveGateway(liveRoomClient = liveRoomClient)
    }

object AppServices {
    var applicationContext: Context? = null
    var container: AppContainer? = null
}

private class SettingsMonitoringConfigProvider(
    private val settingsStore: SettingsStore,
) : MonitoringConfigProvider {
    override suspend fun current(): MonitoringConfig =
        MonitoringConfig(
            roomId = settingsStore.roomId.first(),
            outputMode = settingsStore.outputMode.first(),
            websocketEndpoint = settingsStore.websocketEndpoint.first(),
            websocketUid = settingsStore.websocketUid.first(),
            websocketToken = settingsStore.websocketToken.first(),
            autoReconnectEnabled = settingsStore.autoReconnectEnabled.first(),
            reconnectIntervalMillis = settingsStore.reconnectIntervalSeconds.first().toLong() * 1_000L,
        )
}

private class SettingsOutputModeProvider(
    private val settingsStore: SettingsStore,
) : OutputModeProvider {
    override suspend fun getCurrentMode(): OutputMode =
        settingsStore.outputMode.first()
}

private class SettingsGiftTriggerModeProvider(
    private val settingsStore: SettingsStore,
) : GiftTriggerModeProvider {
    override suspend fun getCurrentMode(): GiftTriggerMode =
        settingsStore.giftTriggerMode.first()
}

private fun buildDefaultRules(): List<TriggerRule> {
    // 默认规则需要与桌面端的事件拆分保持一致，避免新事件回落到旧的通用规则后丢失独立配置能力。
    return buildGiftTierRules() +
        buildSpecialPriceTierRules() +
        listOf(
            TriggerRule(
                id = "like-default",
                name = "点赞默认规则",
                eventType = LiveEventType.LIKE,
                conditions = RuleConditions(
                    likeMultiple = 100,
                ),
                actionBindings = ActionBindings(
                    bluetoothAction = OutputAction.BluetoothWaveformAction("ems-preset-01"),
                    websocketAction = OutputAction.WebSocketCommandAction("command_one"),
                ),
            ),
            TriggerRule(
                id = "danmaku-default",
                name = "弹幕默认规则",
                enabled = false,
                eventType = LiveEventType.DANMAKU,
                cooldownSeconds = 0,
                cooldownScope = CooldownScope.PER_USER,
                conditions = RuleConditions(
                    keywords = emptyList(),
                    matchMode = KeywordMatchMode.ANY,
                ),
                actionBindings = ActionBindings(
                    bluetoothAction = OutputAction.BluetoothWaveformAction("ems-preset-03"),
                    websocketAction = OutputAction.WebSocketCommandAction("command_three"),
                ),
            ),
            createDanmakuGuardRule(
                id = "danmaku-captain-default",
                name = "舰长弹幕规则",
                eventType = LiveEventType.DANMAKU_CAPTAIN,
                minGuardLevel = 3,
                waveformId = "ems-preset-04",
                commandSlot = "command_four",
            ),
            createDanmakuGuardRule(
                id = "danmaku-commander-default",
                name = "提督弹幕规则",
                eventType = LiveEventType.DANMAKU_COMMANDER,
                minGuardLevel = 2,
                waveformId = "ems-preset-05",
                commandSlot = "command_five",
            ),
            createDanmakuGuardRule(
                id = "danmaku-governor-default",
                name = "总督弹幕规则",
                eventType = LiveEventType.DANMAKU_GOVERNOR,
                minGuardLevel = 1,
                waveformId = "ems-preset-09",
                commandSlot = "command_nine",
            ),
        )
}

private fun buildGiftTierRules(): List<TriggerRule> =
    (1..10).map { index ->
        val slot = "command_${fixedSlotName(index)}"
        val presetId = "ems-preset-${index.toString().padStart(2, '0')}"
        val minPrice = when (index) {
            1 -> 0
            2 -> 100
            3 -> 1_000
            4 -> 5_000
            5 -> 10_000
            6 -> 20_000
            7 -> 50_000
            8 -> 100_000
            9 -> 200_000
            else -> 1_000_000
        }
        val maxPrice = when (index) {
            1 -> 99
            2 -> 999
            3 -> 4_999
            4 -> 9_999
            5 -> 19_999
            6 -> 49_999
            7 -> 99_999
            8 -> 199_999
            9 -> 999_999
            else -> null
        }
        TriggerRule(
            id = "gift-tier-${slot.padStart(2, '0')}",
            name = "礼物档位 ${slot.padStart(2, '0')}",
            eventType = LiveEventType.GIFT,
            conditions = RuleConditions(
                minPrice = minPrice,
                maxPrice = maxPrice,
            ),
            actionBindings = createDefaultBindings(
                waveformId = presetId,
                commandSlot = slot,
            ),
        )
    }

private fun buildSpecialPriceTierRules(): List<TriggerRule> =
    listOf(
        SpecialPriceTierDefault("super-chat-tier-01", "醒目留言档位 01", LiveEventType.SUPER_CHAT, "ems-preset-01", "command_one", 30, 49),
        SpecialPriceTierDefault("super-chat-tier-02", "醒目留言档位 02", LiveEventType.SUPER_CHAT, "ems-preset-02", "command_two", 50, 99),
        SpecialPriceTierDefault("super-chat-tier-03", "醒目留言档位 03", LiveEventType.SUPER_CHAT, "ems-preset-03", "command_three", 100, 499),
        SpecialPriceTierDefault("super-chat-tier-04", "醒目留言档位 04", LiveEventType.SUPER_CHAT, "ems-preset-04", "command_four", 500, 999),
        SpecialPriceTierDefault("super-chat-tier-05", "醒目留言档位 05", LiveEventType.SUPER_CHAT, "ems-preset-05", "command_five", 1_000, 1_999),
        SpecialPriceTierDefault("super-chat-tier-06", "醒目留言档位 06", LiveEventType.SUPER_CHAT, "ems-preset-06", "command_six", 2_000, null),
        // Android 端当前只内置 10 组 EMS 预设，这里先按桌面端同档位的指令槽顺序
        // 绑定现有预设，保证上舰/续费多档规则可以直接落地运行。
        SpecialPriceTierDefault("guard-buy-tier-01", "上舰档位 01", LiveEventType.GUARD_BUY, "ems-preset-08", "command_eight", 100_000, 999_999),
        SpecialPriceTierDefault("guard-buy-tier-02", "上舰档位 02", LiveEventType.GUARD_BUY, "ems-preset-09", "command_nine", 1_000_000, 9_999_999),
        SpecialPriceTierDefault("guard-buy-tier-03", "上舰档位 03", LiveEventType.GUARD_BUY, "ems-preset-10", "command_ten", 10_000_000, null),
        SpecialPriceTierDefault("guard-renew-tier-01", "续费档位 01", LiveEventType.GUARD_RENEW, "ems-preset-07", "command_seven", 50_000, 999_999),
        SpecialPriceTierDefault("guard-renew-tier-02", "续费档位 02", LiveEventType.GUARD_RENEW, "ems-preset-08", "command_eight", 1_000_000, 9_999_999),
        SpecialPriceTierDefault("guard-renew-tier-03", "续费档位 03", LiveEventType.GUARD_RENEW, "ems-preset-09", "command_nine", 10_000_000, null),
    ).map(::createSpecialPriceTierRule)

private fun createSpecialPriceTierRule(
    definition: SpecialPriceTierDefault,
): TriggerRule =
    TriggerRule(
        id = definition.id,
        name = definition.name,
        enabled = false,
        eventType = definition.eventType,
        conditions = RuleConditions(
            minPrice = definition.minPrice,
            maxPrice = definition.maxPrice,
        ),
        actionBindings = createDefaultBindings(
            waveformId = definition.waveformId,
            commandSlot = definition.commandSlot,
        ),
    )

private data class SpecialPriceTierDefault(
    val id: String,
    val name: String,
    val eventType: LiveEventType,
    val waveformId: String,
    val commandSlot: String,
    val minPrice: Int,
    val maxPrice: Int?,
)

private fun createDanmakuGuardRule(
    id: String,
    name: String,
    eventType: LiveEventType,
    minGuardLevel: Int,
    waveformId: String,
    commandSlot: String,
): TriggerRule =
    TriggerRule(
        id = id,
        name = name,
        enabled = false,
        eventType = eventType,
        // 舰队弹幕规则默认按用户冷却，避免同一位舰长连续发言把全房间触发都压住。
        cooldownScope = CooldownScope.PER_USER,
        conditions = RuleConditions(
            minGuardLevel = minGuardLevel,
            keywords = emptyList(),
            matchMode = KeywordMatchMode.ANY,
        ),
        actionBindings = createDefaultBindings(
            waveformId = waveformId,
            commandSlot = commandSlot,
        ),
    )

private fun createDefaultBindings(
    waveformId: String,
    commandSlot: String,
): ActionBindings =
    ActionBindings(
        bluetoothAction = OutputAction.BluetoothWaveformAction(waveformId),
        websocketAction = OutputAction.WebSocketCommandAction(commandSlot),
    )

private fun fixedSlotName(index: Int): String =
    when (index) {
        1 -> "one"
        2 -> "two"
        3 -> "three"
        4 -> "four"
        5 -> "five"
        6 -> "six"
        7 -> "seven"
        8 -> "eight"
        9 -> "nine"
        else -> "ten"
    }
