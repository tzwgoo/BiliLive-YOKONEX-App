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
    val giftRules = (1..10).map { index ->
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
            actionBindings = ActionBindings(
                bluetoothAction = OutputAction.BluetoothWaveformAction(presetId),
                websocketAction = OutputAction.WebSocketCommandAction(slot),
            ),
        )
    }

    return giftRules + listOf(
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
            conditions = RuleConditions(
                keywords = emptyList(),
                matchMode = KeywordMatchMode.ANY,
            ),
            actionBindings = ActionBindings(
                bluetoothAction = OutputAction.BluetoothWaveformAction("ems-preset-03"),
                websocketAction = OutputAction.WebSocketCommandAction("command_three"),
            ),
        ),
    )
}

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
