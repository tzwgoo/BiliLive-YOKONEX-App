package com.yokonex.bililive.app.ui.live

import com.yokonex.bililive.app.ui.MainDispatcherRule
import com.yokonex.bililive.data.storage.JsonRuleStore
import com.yokonex.bililive.data.storage.SettingsStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yokonex.bililive.data.live.ThirdPartyLiveGateway
import com.yokonex.bililive.domain.model.CooldownScope
import com.yokonex.bililive.domain.model.GiftTriggerMode
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.service.MonitoringConfig
import com.yokonex.bililive.service.MonitoringConfigProvider
import com.yokonex.bililive.service.ServiceCoordinator
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LiveConfigViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updateRoomId_updatesUiState() {
        val viewModel = LiveConfigViewModel()

        viewModel.updateRoomId("22445566")

        assertEquals("22445566", viewModel.uiState.value.roomId)
    }

    @Test
    fun toggleAutoReconnect_updatesFlag() {
        val viewModel = LiveConfigViewModel()

        viewModel.toggleAutoReconnect(true)

        assertTrue(viewModel.uiState.value.autoReconnect)
    }

    @Test
    fun updateGiftTriggerMode_updatesPreviewState() {
        val viewModel = LiveConfigViewModel()

        viewModel.updateGiftTriggerMode(GiftTriggerMode.BY_QUANTITY)

        assertEquals(GiftTriggerMode.BY_QUANTITY, viewModel.uiState.value.giftTriggerMode)
    }

    @Test
    fun toggleAutoReconnect_withSettingsStore_persistsFlag() = runTest {
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = {
                    Files.createTempFile("live-config-viewmodel", ".preferences_pb").toFile()
                },
            ),
        )
        val viewModel = LiveConfigViewModel(
            settingsStore = settingsStore,
            batteryOptimizationStatusProvider = FakeBatteryOptimizationStatusProvider(
                BatteryOptimizationStatus(
                    supported = false,
                    ignoringBatteryOptimizations = true,
                ),
            ),
        )

        viewModel.toggleAutoReconnect(false)

        assertTrue(!settingsStore.autoReconnectEnabled.first())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun init_withSettingsStore_readsGiftTriggerMode() = runTest {
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = {
                    Files.createTempFile("live-config-viewmodel", ".preferences_pb").toFile()
                },
            ),
        )
        settingsStore.updateGiftTriggerMode(GiftTriggerMode.BY_QUANTITY)

        val viewModel = LiveConfigViewModel(
            settingsStore = settingsStore,
            batteryOptimizationStatusProvider = FakeBatteryOptimizationStatusProvider(
                BatteryOptimizationStatus(
                    supported = false,
                    ignoringBatteryOptimizations = true,
                ),
            ),
        )
        runCurrent()

        assertEquals(GiftTriggerMode.BY_QUANTITY, viewModel.uiState.value.giftTriggerMode)
    }

    @Test
    fun updateGiftTriggerMode_withSettingsStore_persistsMode() = runTest {
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = {
                    Files.createTempFile("live-config-viewmodel", ".preferences_pb").toFile()
                },
            ),
        )
        val viewModel = LiveConfigViewModel(
            settingsStore = settingsStore,
            batteryOptimizationStatusProvider = FakeBatteryOptimizationStatusProvider(
                BatteryOptimizationStatus(
                    supported = false,
                    ignoringBatteryOptimizations = true,
                ),
            ),
        )

        viewModel.updateGiftTriggerMode(GiftTriggerMode.BY_QUANTITY)

        assertEquals(GiftTriggerMode.BY_QUANTITY, settingsStore.giftTriggerMode.first())
    }

    @Test
    fun init_readsBatteryOptimizationState() {
        val viewModel = LiveConfigViewModel(
            batteryOptimizationStatusProvider = FakeBatteryOptimizationStatusProvider(
                BatteryOptimizationStatus(
                    supported = true,
                    ignoringBatteryOptimizations = false,
                ),
            ),
        )

        assertEquals("建议关闭电池优化", viewModel.uiState.value.batteryOptimizationStatus)
        assertTrue(viewModel.uiState.value.shouldShowBatteryOptimizationAction)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun toggleMonitoring_whenIdle_updatesStatusToRunning() = runTest {
        val serviceCoordinator = ServiceCoordinator(
            configProvider = object : MonitoringConfigProvider {
                override suspend fun current(): MonitoringConfig =
                    MonitoringConfig(
                        roomId = "22608112",
                        outputMode = OutputMode.BLUETOOTH,
                    )
            },
            liveGateway = object : ThirdPartyLiveGateway {
                override fun events(roomId: String) = flow<LiveEvent> {
                    awaitCancellation()
                }
            },
            dispatcher = Dispatchers.Main,
        )
        val viewModel = LiveConfigViewModel(serviceCoordinator = serviceCoordinator)

        viewModel.toggleMonitoring()
        runCurrent()

        assertEquals("监听中", viewModel.uiState.value.monitoringStatus)
    }

    @Test
    fun updateLiveEventSettings_updatesPreviewState() {
        val viewModel = LiveConfigViewModel()

        viewModel.updateLikeMultiple("200")
        viewModel.updateDanmakuEnabled(true)
        viewModel.updateDanmakuKeywords("开火,冲冲冲")
        viewModel.updateDanmakuCooldownSeconds("6")
        viewModel.updateDanmakuUserLimitWindowSeconds("30")
        viewModel.updateDanmakuUserLimitMaxTriggers("2")

        assertEquals("200", viewModel.uiState.value.likeMultiple)
        assertTrue(viewModel.uiState.value.danmakuEnabled)
        assertEquals("开火,冲冲冲", viewModel.uiState.value.danmakuKeywords)
        assertEquals("6", viewModel.uiState.value.danmakuCooldownSeconds)
        assertEquals("30", viewModel.uiState.value.danmakuUserLimitWindowSeconds)
        assertEquals("2", viewModel.uiState.value.danmakuUserLimitMaxTriggers)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun updateDanmakuCooldownSeconds_createsPerUserCooldownRuleWhenMissing() = runTest {
        val ruleStore = JsonRuleStore(
            file = Files.createTempFile("live-config-rule-store", ".json").toFile(),
            defaultRules = emptyList(),
        )
        val viewModel = LiveConfigViewModel(
            ruleStore = ruleStore,
            batteryOptimizationStatusProvider = FakeBatteryOptimizationStatusProvider(
                BatteryOptimizationStatus(
                    supported = false,
                    ignoringBatteryOptimizations = true,
                ),
            ),
        )

        viewModel.updateDanmakuCooldownSeconds("6")
        viewModel.updateDanmakuUserLimitWindowSeconds("30")
        viewModel.updateDanmakuUserLimitMaxTriggers("2")
        runCurrent()

        val danmakuRule = ruleStore.rules.value.first { it.id == "danmaku-default" }
        assertEquals(6, danmakuRule.cooldownSeconds)
        assertEquals(CooldownScope.PER_USER, danmakuRule.cooldownScope)
        assertEquals(30, danmakuRule.conditions.userLimitWindowSeconds)
        assertEquals(2, danmakuRule.conditions.userLimitMaxTriggers)
    }

    @Test
    fun refreshBatteryOptimizationStatus_updatesUiState() {
        val provider = FakeBatteryOptimizationStatusProvider(
            BatteryOptimizationStatus(
                supported = true,
                ignoringBatteryOptimizations = false,
            ),
        )
        val viewModel = LiveConfigViewModel(
            batteryOptimizationStatusProvider = provider,
        )

        provider.status = BatteryOptimizationStatus(
            supported = true,
            ignoringBatteryOptimizations = true,
        )
        viewModel.refreshBatteryOptimizationStatus()

        assertEquals("已关闭电池优化", viewModel.uiState.value.batteryOptimizationStatus)
        assertTrue(!viewModel.uiState.value.shouldShowBatteryOptimizationAction)
    }

    @Test
    fun parseDanmakuKeywords_supportsCommaVariantsAndLineBreaks() {
        assertEquals(
            listOf("开火", "冲冲冲", "加速"),
            parseDanmakuKeywords("开火，冲冲冲\n加速"),
        )
    }
}

private class FakeBatteryOptimizationStatusProvider(
    var status: BatteryOptimizationStatus,
) : BatteryOptimizationStatusProvider {
    override fun currentStatus(): BatteryOptimizationStatus = status
}
