package com.yokonex.bililive.domain.usecase

import com.yokonex.bililive.data.bluetooth.BluetoothRepository
import com.yokonex.bililive.data.bluetooth.model.BluetoothConnectionState
import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice
import com.yokonex.bililive.data.bluetooth.model.BluetoothRuntimeStatus
import com.yokonex.bililive.domain.model.ActionBindings
import com.yokonex.bililive.domain.model.CooldownScope
import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.GiftTriggerMode
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.domain.model.RuleConditions
import com.yokonex.bililive.domain.model.TriggerRule
import com.yokonex.bililive.data.websocket.CommandSocketClient
import com.yokonex.bililive.data.websocket.CommandSocketRuntimeInfo
import com.yokonex.bililive.data.websocket.CommandSocketState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ProcessLiveEventUseCaseTest {

    @Test
    fun processLiveEvent_executesBluetoothAction_whenModeIsBluetooth() = runTest {
        val bluetoothRepository = FakeBluetoothRepository()
        val websocketClient = FakeCommandSocketClient()
        val logRepository = FakeEventLogRepository()
        val useCase = ProcessLiveEventUseCase(
            ruleRepository = StaticRuleRepository(
                listOf(
                    TriggerRule(
                        id = "gift-rule",
                        name = "礼物触发蓝牙",
                        eventType = LiveEventType.GIFT,
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "soft_pulse",
                            ),
                        ),
                    ),
                ),
            ),
            outputModeProvider = StaticOutputModeProvider(OutputMode.BLUETOOTH),
            giftTriggerModeProvider = StaticGiftTriggerModeProvider(GiftTriggerMode.SINGLE),
            bluetoothRepository = bluetoothRepository,
            commandSocketClient = websocketClient,
            eventLogRepository = logRepository,
        )

        useCase(
            LiveEvent(
                id = "event-1",
                type = LiveEventType.GIFT,
                timestamp = 1L,
                userId = "1001",
                userName = "tester",
                roomId = "2001",
                payload = EventPayload.GiftPayload(
                    giftName = "小电视",
                    giftNum = 1,
                    price = 100,
                    totalPrice = 100,
                ),
            ),
        )

        assertEquals(listOf("GIFT:soft_pulse#1"), bluetoothRepository.enqueuedWaveforms)
        assertEquals(true, logRepository.records.last().outputSuccess)
    }

    @Test
    fun processLiveEvent_recordsFailure_withoutThrowing() = runTest {
        val bluetoothRepository = FakeBluetoothRepository(
            failure = IllegalStateException("boom"),
        )
        val useCase = ProcessLiveEventUseCase(
            ruleRepository = StaticRuleRepository(
                listOf(
                    TriggerRule(
                        id = "gift-rule",
                        name = "礼物触发蓝牙",
                        eventType = LiveEventType.GIFT,
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "soft_pulse",
                            ),
                        ),
                    ),
                ),
            ),
            outputModeProvider = StaticOutputModeProvider(OutputMode.BLUETOOTH),
            giftTriggerModeProvider = StaticGiftTriggerModeProvider(GiftTriggerMode.SINGLE),
            bluetoothRepository = bluetoothRepository,
            commandSocketClient = FakeCommandSocketClient(),
            eventLogRepository = FakeEventLogRepository(),
        )

        try {
            useCase(
                LiveEvent(
                    id = "event-2",
                    type = LiveEventType.GIFT,
                    timestamp = 2L,
                    userId = "1002",
                    userName = "tester",
                    roomId = "2001",
                    payload = EventPayload.GiftPayload(
                        giftName = "小电视",
                        giftNum = 1,
                        price = 200,
                        totalPrice = 200,
                    ),
                ),
            )
        } catch (error: Throwable) {
            fail("useCase 不应该抛出异常: $error")
        }
    }

    @Test
    fun processLiveEvent_skipsDanmakuDuringCooldown() = runTest {
        val bluetoothRepository = FakeBluetoothRepository()
        val logRepository = FakeEventLogRepository()
        val useCase = ProcessLiveEventUseCase(
            ruleRepository = StaticRuleRepository(
                listOf(
                    TriggerRule(
                        id = "danmaku-rule",
                        name = "弹幕触发蓝牙",
                        eventType = LiveEventType.DANMAKU,
                        cooldownSeconds = 5,
                        conditions = RuleConditions(
                            keywords = listOf("开火"),
                        ),
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "ems-preset-03",
                            ),
                        ),
                    ),
                ),
            ),
            outputModeProvider = StaticOutputModeProvider(OutputMode.BLUETOOTH),
            giftTriggerModeProvider = StaticGiftTriggerModeProvider(GiftTriggerMode.SINGLE),
            bluetoothRepository = bluetoothRepository,
            commandSocketClient = FakeCommandSocketClient(),
            eventLogRepository = logRepository,
        )

        val event = LiveEvent(
            id = "event-danmaku-1",
            type = LiveEventType.DANMAKU,
            timestamp = 10L,
            userId = "1003",
            userName = "tester",
            roomId = "2001",
            payload = EventPayload.DanmakuPayload(message = "开火"),
        )

        useCase(event)
        useCase(event.copy(id = "event-danmaku-2", timestamp = 12L))

        assertEquals(listOf("DANMAKU:ems-preset-03#1"), bluetoothRepository.enqueuedWaveforms)
        assertEquals("cooldown_skipped", logRepository.records.last().outputMessage)
    }

    @Test
    fun processLiveEvent_perUserCooldownAllowsDifferentUsersToTriggerIndependently() = runTest {
        val bluetoothRepository = FakeBluetoothRepository()
        val useCase = ProcessLiveEventUseCase(
            ruleRepository = StaticRuleRepository(
                listOf(
                    TriggerRule(
                        id = "danmaku-rule",
                        name = "弹幕触发蓝牙",
                        eventType = LiveEventType.DANMAKU,
                        cooldownSeconds = 5,
                        cooldownScope = CooldownScope.PER_USER,
                        conditions = RuleConditions(
                            keywords = listOf("开火"),
                        ),
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "ems-preset-03",
                            ),
                        ),
                    ),
                ),
            ),
            outputModeProvider = StaticOutputModeProvider(OutputMode.BLUETOOTH),
            giftTriggerModeProvider = StaticGiftTriggerModeProvider(GiftTriggerMode.SINGLE),
            bluetoothRepository = bluetoothRepository,
            commandSocketClient = FakeCommandSocketClient(),
            eventLogRepository = FakeEventLogRepository(),
        )

        useCase(
            LiveEvent(
                id = "event-danmaku-1",
                type = LiveEventType.DANMAKU,
                timestamp = 10L,
                userId = "1003",
                userName = "user-a",
                roomId = "2001",
                payload = EventPayload.DanmakuPayload(message = "开火"),
            ),
        )
        useCase(
            LiveEvent(
                id = "event-danmaku-2",
                type = LiveEventType.DANMAKU,
                timestamp = 12L,
                userId = "1004",
                userName = "user-b",
                roomId = "2001",
                payload = EventPayload.DanmakuPayload(message = "开火"),
            ),
        )

        assertEquals(
            listOf("DANMAKU:ems-preset-03#1", "DANMAKU:ems-preset-03#1"),
            bluetoothRepository.enqueuedWaveforms,
        )
    }

    @Test
    fun processLiveEvent_blocksDanmakuWhenUserWindowLimitReached() = runTest {
        val bluetoothRepository = FakeBluetoothRepository()
        val logRepository = FakeEventLogRepository()
        val useCase = ProcessLiveEventUseCase(
            ruleRepository = StaticRuleRepository(
                listOf(
                    TriggerRule(
                        id = "danmaku-rule",
                        name = "弹幕触发蓝牙",
                        eventType = LiveEventType.DANMAKU,
                        conditions = RuleConditions(
                            keywords = listOf("开火"),
                            userLimitWindowSeconds = 30,
                            userLimitMaxTriggers = 2,
                        ),
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "ems-preset-03",
                            ),
                        ),
                    ),
                ),
            ),
            outputModeProvider = StaticOutputModeProvider(OutputMode.BLUETOOTH),
            giftTriggerModeProvider = StaticGiftTriggerModeProvider(GiftTriggerMode.SINGLE),
            bluetoothRepository = bluetoothRepository,
            commandSocketClient = FakeCommandSocketClient(),
            eventLogRepository = logRepository,
        )

        listOf(10L, 20L, 25L).forEachIndexed { index, timestamp ->
            useCase(
                LiveEvent(
                    id = "event-danmaku-limit-$index",
                    type = LiveEventType.DANMAKU,
                    timestamp = timestamp,
                    userId = "1003",
                    userName = "tester",
                    roomId = "2001",
                    payload = EventPayload.DanmakuPayload(message = "开火"),
                ),
            )
        }

        assertEquals(
            listOf("DANMAKU:ems-preset-03#1", "DANMAKU:ems-preset-03#1"),
            bluetoothRepository.enqueuedWaveforms,
        )
        assertEquals("user_limit_skipped", logRepository.records.last().outputMessage)
    }

    @Test
    fun processLiveEvent_usesGuardOverrideWaveformForGiftPayload() = runTest {
        val bluetoothRepository = FakeBluetoothRepository()
        val useCase = ProcessLiveEventUseCase(
            ruleRepository = StaticRuleRepository(
                listOf(
                    TriggerRule(
                        id = "gift-rule",
                        name = "礼物触发蓝牙",
                        eventType = LiveEventType.GIFT,
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "ems-preset-01",
                            ),
                            guardWaveformIds = mapOf(
                                3 to "ems-preset-04",
                            ),
                        ),
                    ),
                ),
            ),
            outputModeProvider = StaticOutputModeProvider(OutputMode.BLUETOOTH),
            giftTriggerModeProvider = StaticGiftTriggerModeProvider(GiftTriggerMode.SINGLE),
            bluetoothRepository = bluetoothRepository,
            commandSocketClient = FakeCommandSocketClient(),
            eventLogRepository = FakeEventLogRepository(),
        )

        useCase(
            LiveEvent(
                id = "gift-guard-waveform",
                type = LiveEventType.GIFT,
                timestamp = 10L,
                userId = "1009",
                userName = "captain-user",
                roomId = "2001",
                payload = EventPayload.GiftPayload(
                    giftName = "小电视",
                    giftNum = 1,
                    price = 100,
                    totalPrice = 100,
                    guardLevel = 3,
                    guardLabel = "舰长",
                ),
            ),
        )

        assertEquals(listOf("GIFT:ems-preset-04#1"), bluetoothRepository.enqueuedWaveforms)
    }

    @Test
    fun processLiveEvent_triggersLikeWhenCrossingNewMultipleThreshold() = runTest {
        val bluetoothRepository = FakeBluetoothRepository()
        val logRepository = FakeEventLogRepository()
        val useCase = ProcessLiveEventUseCase(
            ruleRepository = StaticRuleRepository(
                listOf(
                    TriggerRule(
                        id = "like-rule",
                        name = "点赞触发蓝牙",
                        eventType = LiveEventType.LIKE,
                        conditions = com.yokonex.bililive.domain.model.RuleConditions(
                            likeMultiple = 10,
                        ),
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "ems-preset-01",
                            ),
                        ),
                    ),
                ),
            ),
            outputModeProvider = StaticOutputModeProvider(OutputMode.BLUETOOTH),
            giftTriggerModeProvider = StaticGiftTriggerModeProvider(GiftTriggerMode.SINGLE),
            bluetoothRepository = bluetoothRepository,
            commandSocketClient = FakeCommandSocketClient(),
            eventLogRepository = logRepository,
        )

        useCase(
            LiveEvent(
                id = "event-like-1",
                type = LiveEventType.LIKE,
                timestamp = 10L,
                userId = "1004",
                userName = "tester",
                roomId = "2001",
                payload = EventPayload.LikePayload(
                    likeCount = 9,
                    likeText = "点赞了",
                ),
            ),
        )
        useCase(
            LiveEvent(
                id = "event-like-2",
                type = LiveEventType.LIKE,
                timestamp = 11L,
                userId = "1004",
                userName = "tester",
                roomId = "2001",
                payload = EventPayload.LikePayload(
                    likeCount = 13,
                    likeText = "点赞了",
                ),
            ),
        )

        assertEquals(listOf("LIKE:ems-preset-01#1"), bluetoothRepository.enqueuedWaveforms)
        assertEquals("ok", logRepository.records.last().outputMessage)
    }

    @Test
    fun processLiveEvent_accumulatesLikeDeltaWhenCountUnavailable() = runTest {
        val bluetoothRepository = FakeBluetoothRepository()
        val logRepository = FakeEventLogRepository()
        val useCase = ProcessLiveEventUseCase(
            ruleRepository = StaticRuleRepository(
                listOf(
                    TriggerRule(
                        id = "like-rule",
                        name = "点赞触发蓝牙",
                        eventType = LiveEventType.LIKE,
                        conditions = com.yokonex.bililive.domain.model.RuleConditions(
                            likeMultiple = 3,
                        ),
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "ems-preset-02",
                            ),
                        ),
                    ),
                ),
            ),
            outputModeProvider = StaticOutputModeProvider(OutputMode.BLUETOOTH),
            giftTriggerModeProvider = StaticGiftTriggerModeProvider(GiftTriggerMode.SINGLE),
            bluetoothRepository = bluetoothRepository,
            commandSocketClient = FakeCommandSocketClient(),
            eventLogRepository = logRepository,
        )

        repeat(3) { index ->
            useCase(
                LiveEvent(
                    id = "event-like-delta-$index",
                    type = LiveEventType.LIKE,
                    timestamp = 20L + index,
                    userId = "1005",
                    userName = "tester",
                    roomId = "2002",
                    payload = EventPayload.LikePayload(
                        likeCount = 0,
                        likeText = "点赞了",
                        likeDelta = 1,
                    ),
                ),
            )
        }

        assertEquals(listOf("LIKE:ems-preset-02#1"), bluetoothRepository.enqueuedWaveforms)
        assertEquals("ok", logRepository.records.last().outputMessage)
    }

    @Test
    fun processLiveEvent_repeatsGiftActionByQuantity_whenGlobalModeIsByQuantity() = runTest {
        val bluetoothRepository = FakeBluetoothRepository()
        val useCase = ProcessLiveEventUseCase(
            ruleRepository = StaticRuleRepository(
                listOf(
                    TriggerRule(
                        id = "gift-rule",
                        name = "礼物触发蓝牙",
                        eventType = LiveEventType.GIFT,
                        conditions = com.yokonex.bililive.domain.model.RuleConditions(
                            minPrice = 100,
                            maxPrice = 100,
                        ),
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "gift-wave",
                            ),
                        ),
                    ),
                ),
            ),
            outputModeProvider = StaticOutputModeProvider(OutputMode.BLUETOOTH),
            giftTriggerModeProvider = StaticGiftTriggerModeProvider(GiftTriggerMode.BY_QUANTITY),
            bluetoothRepository = bluetoothRepository,
            commandSocketClient = FakeCommandSocketClient(),
            eventLogRepository = FakeEventLogRepository(),
        )

        useCase(
            LiveEvent(
                id = "gift-quantity",
                type = LiveEventType.GIFT,
                timestamp = 3L,
                userId = "1006",
                userName = "tester",
                roomId = "2001",
                payload = EventPayload.GiftPayload(
                    giftName = "辣条",
                    giftNum = 3,
                    price = 100,
                    totalPrice = 300,
                ),
            ),
        )

        assertEquals(
            listOf("GIFT:gift-wave#3"),
            bluetoothRepository.enqueuedWaveforms,
        )
    }

    @Test
    fun processLiveEvent_matchesGiftTierByUnitPrice_notTotalPrice() = runTest {
        val bluetoothRepository = FakeBluetoothRepository()
        val useCase = ProcessLiveEventUseCase(
            ruleRepository = StaticRuleRepository(
                listOf(
                    TriggerRule(
                        id = "gift-rule",
                        name = "礼物触发蓝牙",
                        eventType = LiveEventType.GIFT,
                        conditions = com.yokonex.bililive.domain.model.RuleConditions(
                            minPrice = 100,
                            maxPrice = 199,
                        ),
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "gift-wave",
                            ),
                        ),
                    ),
                ),
            ),
            outputModeProvider = StaticOutputModeProvider(OutputMode.BLUETOOTH),
            giftTriggerModeProvider = StaticGiftTriggerModeProvider(GiftTriggerMode.SINGLE),
            bluetoothRepository = bluetoothRepository,
            commandSocketClient = FakeCommandSocketClient(),
            eventLogRepository = FakeEventLogRepository(),
        )

        useCase(
            LiveEvent(
                id = "gift-price",
                type = LiveEventType.GIFT,
                timestamp = 4L,
                userId = "1007",
                userName = "tester",
                roomId = "2001",
                payload = EventPayload.GiftPayload(
                    giftName = "小心心",
                    giftNum = 10,
                    price = 100,
                    totalPrice = 1_000,
                ),
            ),
        )

        assertEquals(listOf("GIFT:gift-wave#1"), bluetoothRepository.enqueuedWaveforms)
    }

    @Test
    fun processLiveEvent_prefersSpecificGiftSubtypeRule_overBaseGiftRule() = runTest {
        val bluetoothRepository = FakeBluetoothRepository()
        val useCase = ProcessLiveEventUseCase(
            ruleRepository = StaticRuleRepository(
                listOf(
                    TriggerRule(
                        id = "gift-rule",
                        name = "礼物通用规则",
                        eventType = LiveEventType.GIFT,
                        conditions = RuleConditions(
                            minPrice = 30,
                            maxPrice = 200,
                        ),
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "gift-wave",
                            ),
                        ),
                    ),
                    TriggerRule(
                        id = "super-chat-rule",
                        name = "醒目留言专属规则",
                        eventType = LiveEventType.SUPER_CHAT,
                        conditions = RuleConditions(
                            minPrice = 30,
                            maxPrice = 200,
                        ),
                        actionBindings = ActionBindings(
                            bluetoothAction = com.yokonex.bililive.domain.model.OutputAction.BluetoothWaveformAction(
                                waveformId = "super-chat-wave",
                            ),
                        ),
                    ),
                ),
            ),
            outputModeProvider = StaticOutputModeProvider(OutputMode.BLUETOOTH),
            giftTriggerModeProvider = StaticGiftTriggerModeProvider(GiftTriggerMode.SINGLE),
            bluetoothRepository = bluetoothRepository,
            commandSocketClient = FakeCommandSocketClient(),
            eventLogRepository = FakeEventLogRepository(),
        )

        useCase(
            LiveEvent(
                id = "sc-1",
                type = LiveEventType.SUPER_CHAT,
                timestamp = 10L,
                userId = "1008",
                userName = "sc-user",
                roomId = "2001",
                payload = EventPayload.GiftPayload(
                    giftName = "醒目留言",
                    giftNum = 1,
                    price = 100,
                    totalPrice = 100,
                    message = "测试 SC",
                ),
            ),
        )

        assertEquals(listOf("SUPER_CHAT:super-chat-wave#1"), bluetoothRepository.enqueuedWaveforms)
    }

    private class StaticRuleRepository(
        private val rules: List<TriggerRule>,
    ) : RuleRepository {
        override suspend fun getEnabledRules(): List<TriggerRule> = rules
    }

    private class StaticOutputModeProvider(
        private val mode: OutputMode,
    ) : OutputModeProvider {
        override suspend fun getCurrentMode(): OutputMode = mode
    }

    private class StaticGiftTriggerModeProvider(
        private val mode: GiftTriggerMode,
    ) : GiftTriggerModeProvider {
        override suspend fun getCurrentMode(): GiftTriggerMode = mode
    }

    private class FakeBluetoothRepository(
        private val failure: Throwable? = null,
    ) : BluetoothRepository {
        override val connectionState: StateFlow<BluetoothConnectionState> =
            MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
        override val devices: StateFlow<List<BluetoothDevice>> =
            MutableStateFlow(emptyList())
        override val runtimeStatus: StateFlow<BluetoothRuntimeStatus> =
            MutableStateFlow(BluetoothRuntimeStatus())

        val playedWaveforms = mutableListOf<String>()
        val enqueuedWaveforms = mutableListOf<String>()

        override suspend fun scan(): List<BluetoothDevice> = emptyList()

        override suspend fun connect(deviceId: String) = Unit

        override suspend fun disconnect() = Unit

        override suspend fun playWaveform(
            waveformId: String,
            repeatCount: Int,
        ) {
            failure?.let { throw it }
            playedWaveforms += "$waveformId#$repeatCount"
        }

        override suspend fun enqueueWaveform(
            waveformId: String,
            eventType: LiveEventType,
            repeatCount: Int,
        ) {
            failure?.let { throw it }
            enqueuedWaveforms += "${eventType.name}:$waveformId#$repeatCount"
        }

        override suspend fun clearActiveWaveforms() = Unit

        override fun setMixModeEnabled(enabled: Boolean) = Unit
    }

    private class FakeCommandSocketClient : CommandSocketClient {
        override val connectionState: StateFlow<CommandSocketState> =
            MutableStateFlow(CommandSocketState.DISCONNECTED)
        override val runtimeInfo: StateFlow<CommandSocketRuntimeInfo> =
            MutableStateFlow(CommandSocketRuntimeInfo())

        val commands = mutableListOf<String>()

        override suspend fun connect(wsUrl: String, uid: String, token: String) = Unit

        override suspend fun disconnect() = Unit

        override suspend fun sendCommand(
            commandSlot: String,
            repeatCount: Int,
        ) {
            commands += "$commandSlot#$repeatCount"
        }
    }

    private class FakeEventLogRepository : EventLogRepository {
        val records = mutableListOf<ProcessedEventRecord>()

        override suspend fun record(record: ProcessedEventRecord) {
            records += record
        }
    }
}
