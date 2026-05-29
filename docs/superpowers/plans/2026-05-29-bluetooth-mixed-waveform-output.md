# Bluetooth Mixed Waveform Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为安卓端蓝牙输出链路增加多事件混合波形能力，在礼物、弹幕、点赞同时触发时按时间片合成输出，并兼容 `ems_v1` 与 `ems_v2` 协议差异。

**Architecture:** 保持现有规则命中、事件采集和波形数据结构不变，把改造集中在蓝牙执行链路。先为协议编码器补单帧输出和失败测试，再新增独立的混波运行时与活动层模型，最后扩展仓库、用例和输出页状态，让蓝牙模式可以在串行播放与混波模式之间切换。

**Tech Stack:** Kotlin、Coroutines、StateFlow、JUnit4、Jetpack Compose、Android BLE API

---

## File Structure

### Create

- `app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothMixRuntime.kt`
- `app/src/main/java/com/yokonex/bililive/data/bluetooth/MixPolicy.kt`
- `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/ActiveWaveformLayer.kt`
- `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/MixFrame.kt`
- `app/src/test/java/com/yokonex/bililive/data/bluetooth/BluetoothMixRuntimeTest.kt`

### Modify

- `app/src/main/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoder.kt`
- `app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothRepository.kt`
- `app/src/main/java/com/yokonex/bililive/data/bluetooth/DefaultBluetoothRepository.kt`
- `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/BluetoothRuntimeStatus.kt`
- `app/src/main/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCase.kt`
- `app/src/main/java/com/yokonex/bililive/app/ui/output/OutputConfigViewModel.kt`
- `app/src/main/java/com/yokonex/bililive/app/ui/output/OutputConfigScreen.kt`
- `app/src/test/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoderTest.kt`
- `app/src/test/java/com/yokonex/bililive/data/bluetooth/DefaultBluetoothRepositoryTest.kt`
- `app/src/test/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCaseTest.kt`
- `app/src/test/java/com/yokonex/bililive/app/ui/output/OutputConfigViewModelTest.kt`

### Responsibilities

- `EmsProtocolEncoder.kt`: 保留整条波形编码，同时新增单帧编码入口，并把 `ems_v1` 的 `A/B/AB` 映射语义固定下来。
- `MixPolicy.kt`: 收敛礼物/弹幕/点赞权重、优先级、层数上限、普通上限 `130`、礼物主层上限 `180` 与默认 tick `50ms`。
- `ActiveWaveformLayer.kt`: 描述活动波形层，并提供“当前时间点落在哪个 step”的纯 Kotlin 计算。
- `MixFrame.kt`: 承载当前时间片最终要发给协议编码器的输出帧。
- `BluetoothMixRuntime.kt`: 管理活动层、选择主层、计算混波结果、写 BLE 包并在结束时发 stop。
- `BluetoothRepository.kt` / `DefaultBluetoothRepository.kt`: 把当前“整条波形播放”能力演进为可切换的串行/混波执行入口。
- `ProcessLiveEventUseCase.kt`: 从直接 `playWaveform()` 改为按事件类型入队蓝牙混波事件。
- `BluetoothRuntimeStatus.kt` / `OutputConfigViewModel.kt` / `OutputConfigScreen.kt`: 暴露并展示混波模式、活动层数、主层事件类型、当前上限和混合强度。

### Scope Check

本次 spec 只覆盖一个子系统：蓝牙执行链路。规则存储、波形编辑器、直播事件采集与前台服务保活均不拆成独立计划，范围适合一份实现计划完成。

### Task 1: 为协议单帧编码补失败测试

**Files:**
- Modify: `app/src/test/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoderTest.kt`

- [ ] **Step 1: 为 `ems_v2` 单帧编码写失败测试**

```kotlin
@Test
fun createStepPacket_forV2_usesIndependentChannelStrengths() {
    val encoder = EmsProtocolEncoder()

    val packet = encoder.createStepPacket(
        step = WaveformStep(
            durationMs = 50,
            channelA = 120,
            channelAMode = 0x01,
            channelB = 80,
            channelBMode = 0x01,
        ),
        protocol = "ems_v2",
        signalMode = WaveformSignalMode.FIXED,
    )

    assertArrayEquals(
        byteArrayOf(
            0x35,
            0x11,
            0x01,
            0x00,
            0x78,
            0x01,
            0x00,
            0x50,
            0x01,
            0x69,
        ),
        packet,
    )
}
```

- [ ] **Step 2: 为 `ems_v1` 的 `A / B / AB` 映射写失败测试**

```kotlin
@Test
fun createStepPacket_forV1_usesAbChannelWithSharedStrength() {
    val encoder = EmsProtocolEncoder()

    val packet = encoder.createStepPacket(
        step = WaveformStep(
            durationMs = 50,
            channelA = 90,
            channelAMode = 0x11,
            channelAFrequency = 10,
            channelAPulseWidth = 5,
            channelB = 130,
            channelBMode = 0x11,
            channelBFrequency = 20,
            channelBPulseWidth = 8,
        ),
        protocol = "ems_v1",
        signalMode = WaveformSignalMode.FIXED,
    )

    assertEquals(0x03, packet[2].toInt() and 0xFF)
    val strength = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
    assertEquals(130, strength)
}
```

- [ ] **Step 3: 运行编码器测试确认失败**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.yokonex.bililive.data.bluetooth.EmsProtocolEncoderTest"`
Expected: FAIL，提示 `createStepPacket` 尚未定义或断言不匹配

### Task 2: 为混波策略和运行时补失败测试

**Files:**
- Create: `app/src/test/java/com/yokonex/bililive/data/bluetooth/BluetoothMixRuntimeTest.kt`
- Modify: `app/src/test/java/com/yokonex/bililive/data/bluetooth/DefaultBluetoothRepositoryTest.kt`

- [ ] **Step 1: 为主层优先级与上限切换写失败测试**

```kotlin
@Test
fun nextFrame_prefersGiftLeaderAndRaisesCapTo180() = runTest {
    val runtime = createRuntime()

    runtime.enqueueLayer(giftLayer(startedAtElapsedMs = 0L))
    runtime.enqueueLayer(danmakuLayer(startedAtElapsedMs = 0L))

    val frame = runtime.nextFrame(nowElapsedMs = 50L)

    assertEquals(LiveEventType.GIFT, frame.leaderEventType)
    assertEquals(180, frame.cap)
}
```

- [ ] **Step 2: 为次层权重和普通上限写失败测试**

```kotlin
@Test
fun nextFrame_appliesDanmakuAndLikeWeightsWithNormalCap130() = runTest {
    val runtime = createRuntime()

    runtime.enqueueLayer(danmakuLayer(channelA = 100, channelB = 60))
    runtime.enqueueLayer(likeLayer(channelA = 50, channelB = 50))

    val frame = runtime.nextFrame(nowElapsedMs = 50L)

    assertEquals(130, frame.cap)
    assertEquals(110, frame.channelA)
    assertEquals(70, frame.channelB)
}
```

- [ ] **Step 3: 为 `ems_v1` 的 `AB` 同步输出写失败测试**

```kotlin
@Test
fun writeMixedFrame_forV1_usesAbMappingInsteadOfAlternatingChannels() = runTest {
    val bleManager = FakeAndroidBleManager()
    val runtime = createRuntime(bleManager = bleManager, protocol = "ems_v1")

    runtime.enqueueLayer(giftLayer(channelA = 90, channelB = 130))
    runtime.tick(nowElapsedMs = 50L)

    val packet = bleManager.writes.single()
    assertEquals(0x03, packet[2].toInt() and 0xFF)
}
```

- [ ] **Step 4: 运行混波相关测试确认失败**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.yokonex.bililive.data.bluetooth.BluetoothMixRuntimeTest" --tests "com.yokonex.bililive.data.bluetooth.DefaultBluetoothRepositoryTest"`
Expected: FAIL，提示 `BluetoothMixRuntime`、活动层或混波接口尚未实现

### Task 3: 实现协议单帧编码和混波模型

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/MixPolicy.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/ActiveWaveformLayer.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/MixFrame.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoder.kt`

- [ ] **Step 1: 在 `MixPolicy.kt` 固定权重、优先级、上限和 tick 常量**

```kotlin
object MixPolicy {
    const val DEFAULT_TICK_MS = 50L
    const val NORMAL_CAP = 130
    const val GIFT_LEADER_CAP = 180
    const val MAX_ACTIVE_LAYERS = 4

    fun weightOf(eventType: LiveEventType): Double = when (eventType) {
        LiveEventType.GIFT -> 1.0
        LiveEventType.DANMAKU -> 0.4
        LiveEventType.LIKE -> 0.2
        LiveEventType.SYSTEM -> 0.0
    }
}
```

- [ ] **Step 2: 在 `ActiveWaveformLayer.kt` 实现当前 step 查询和重复次数结束判断**

```kotlin
fun currentStepAt(nowElapsedMs: Long): WaveformStep? {
    val elapsed = (nowElapsedMs - startedAtElapsedMs).coerceAtLeast(0L)
    val totalDuration = waveform.steps.sumOf { it.durationMs }.coerceAtLeast(1)
    val playbackDuration = totalDuration * repeatCount.coerceAtLeast(1)
    if (elapsed >= playbackDuration) return null
    val offsetInLoop = (elapsed % totalDuration).toInt()
    var consumed = 0
    return waveform.steps.first { step ->
        consumed += step.durationMs
        offsetInLoop < consumed
    }
}
```

- [ ] **Step 3: 在 `MixFrame.kt` 定义输出帧结构**

```kotlin
data class MixFrame(
    val channelA: Int,
    val channelB: Int,
    val channelAMode: Int,
    val channelAFrequency: Int,
    val channelAPulseWidth: Int,
    val channelBMode: Int,
    val channelBFrequency: Int,
    val channelBPulseWidth: Int,
    val cap: Int,
    val leaderEventType: LiveEventType?,
)
```

- [ ] **Step 4: 在 `EmsProtocolEncoder.kt` 新增 `createStepPacket` 并实现 `ems_v1`/`ems_v2` 两条路径**

```kotlin
fun createStepPacket(
    step: WaveformStep,
    protocol: String,
    signalMode: WaveformSignalMode,
): ByteArray = when {
    protocol == "ems_v1" -> createV1MixedPacket(step)
    signalMode == WaveformSignalMode.REALTIME -> createV2RealtimePacket(step)
    else -> createV2FixedPacket(step)
}
```

- [ ] **Step 5: 重新运行编码器测试确认通过**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.yokonex.bililive.data.bluetooth.EmsProtocolEncoderTest"`
Expected: PASS

- [ ] **Step 6: 提交模型与编码器改动**

```bash
git add app/src/main/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoder.kt app/src/main/java/com/yokonex/bililive/data/bluetooth/MixPolicy.kt app/src/main/java/com/yokonex/bililive/data/bluetooth/model/ActiveWaveformLayer.kt app/src/main/java/com/yokonex/bililive/data/bluetooth/model/MixFrame.kt app/src/test/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoderTest.kt
git commit -m "feat(bluetooth): 补充混波模型与单帧协议编码" -m "新增混波策略、活动层和输出帧模型，固定礼物/弹幕/点赞的权重、优先级和上限规则。" -m "为协议编码器补充单帧输出入口，并实现 ems_v1 的 A/B/AB 同步映射与 ems_v2 的独立双通道路径。"
```

### Task 4: 实现混波运行时

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothMixRuntime.kt`
- Modify: `app/src/test/java/com/yokonex/bililive/data/bluetooth/BluetoothMixRuntimeTest.kt`

- [ ] **Step 1: 先在 `BluetoothMixRuntimeTest.kt` 补全 stop、层数上限和主层切换失败测试**

```kotlin
@Test
fun tick_sendsStopOnceWhenAllLayersFinished() = runTest {
    val bleManager = FakeAndroidBleManager()
    val runtime = createRuntime(bleManager = bleManager)

    runtime.enqueueLayer(shortGiftLayer(repeatCount = 1))
    runtime.tick(nowElapsedMs = 500L)

    assertEquals(1, bleManager.stopWrites.size)
}
```

- [ ] **Step 2: 在 `BluetoothMixRuntime.kt` 实现活动层入队和淘汰策略**

```kotlin
fun enqueueLayer(layer: ActiveWaveformLayer) {
    val existing = activeLayers.toMutableList()
    if (existing.size >= MixPolicy.MAX_ACTIVE_LAYERS) {
        val candidate = existing.minWith(compareBy<ActiveWaveformLayer> { it.priority }.thenByDescending { it.startedAtElapsedMs })
        if (candidate.priority >= layer.priority) return
        existing.remove(candidate)
    }
    existing += layer
    activeLayers = existing
}
```

- [ ] **Step 3: 实现 `nextFrame`，按 leader/follower 计算 `A/B` 混波结果**

```kotlin
fun nextFrame(nowElapsedMs: Long): MixFrame? {
    val alive = activeLayers.mapNotNull { layer -> layer.currentStepAt(nowElapsedMs)?.let { step -> layer to step } }
    if (alive.isEmpty()) return null
    val leader = alive.maxWith(compareBy<Pair<ActiveWaveformLayer, WaveformStep>> { it.first.priority }.thenBy { -it.first.startedAtElapsedMs })
    val cap = if (leader.first.eventType == LiveEventType.GIFT) MixPolicy.GIFT_LEADER_CAP else MixPolicy.NORMAL_CAP
    val mixedA = alive.sumOf { (layer, step) -> step.channelA * if (layer.id == leader.first.id) 1.0 else layer.weight }
    val mixedB = alive.sumOf { (layer, step) -> step.channelB * if (layer.id == leader.first.id) 1.0 else layer.weight }
    return MixFrame(
        channelA = mixedA.roundToInt().coerceIn(0, cap),
        channelB = mixedB.roundToInt().coerceIn(0, cap),
        channelAMode = leader.second.channelAMode,
        channelAFrequency = leader.second.channelAFrequency,
        channelAPulseWidth = leader.second.channelAPulseWidth,
        channelBMode = leader.second.channelBMode,
        channelBFrequency = leader.second.channelBFrequency,
        channelBPulseWidth = leader.second.channelBPulseWidth,
        cap = cap,
        leaderEventType = leader.first.eventType,
    )
}
```

- [ ] **Step 4: 实现 `tick`，在有帧时写单帧包、无帧时只发一次 stop**

```kotlin
suspend fun tick(nowElapsedMs: Long) {
    val frame = nextFrame(nowElapsedMs)
    if (frame == null) {
        if (!hasStopped) {
            bleManager.write(protocolEncoder.createStopPacket(protocol))
            hasStopped = true
        }
        return
    }
    hasStopped = false
    bleManager.write(protocolEncoder.createStepPacket(frame.toWaveformStep(), protocol, signalMode))
}
```

- [ ] **Step 5: 运行混波运行时测试确认通过**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.yokonex.bililive.data.bluetooth.BluetoothMixRuntimeTest"`
Expected: PASS

- [ ] **Step 6: 提交混波运行时改动**

```bash
git add app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothMixRuntime.kt app/src/test/java/com/yokonex/bililive/data/bluetooth/BluetoothMixRuntimeTest.kt
git commit -m "feat(bluetooth): 新增蓝牙混波运行时" -m "实现活动层入队、主层选择、权重混波、130/180 上限切换与 stop 收尾逻辑。" -m "让蓝牙链路具备按时间片计算多事件输出帧的能力，为后续仓库接线提供稳定执行核心。"
```

### Task 5: 接入仓库与用例

**Files:**
- Modify: `app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothRepository.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/data/bluetooth/DefaultBluetoothRepository.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCase.kt`
- Modify: `app/src/test/java/com/yokonex/bililive/data/bluetooth/DefaultBluetoothRepositoryTest.kt`
- Modify: `app/src/test/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCaseTest.kt`

- [ ] **Step 1: 为 `enqueueWaveform` 和混波模式切换写失败测试**

```kotlin
@Test
fun enqueueWaveform_whenMixModeEnabled_enqueuesLayerInsteadOfRunningSerialPlayback() = runTest {
    val repository = createRepository(mixModeEnabled = true)

    repository.enqueueWaveform(
        waveformId = "ems-preset-01",
        eventType = LiveEventType.GIFT,
        repeatCount = 2,
    )

    assertEquals(1, repository.mixRuntime.enqueuedLayers.size)
}
```

- [ ] **Step 2: 扩展 `BluetoothRepository.kt` 接口，新增混波控制入口**

```kotlin
suspend fun enqueueWaveform(
    waveformId: String,
    eventType: LiveEventType,
    repeatCount: Int = 1,
)

suspend fun clearActiveWaveforms()

fun setMixModeEnabled(enabled: Boolean)
```

- [ ] **Step 3: 在 `DefaultBluetoothRepository.kt` 接入 `BluetoothMixRuntime`，保留串行回退路径**

```kotlin
override suspend fun enqueueWaveform(
    waveformId: String,
    eventType: LiveEventType,
    repeatCount: Int,
) {
    val waveform = resolveWaveform(waveformId)
    if (!mixModeEnabled) {
        playResolvedWaveformSerially(waveform, repeatCount)
        return
    }
    mixRuntime.enqueueWaveform(
        waveform = waveform,
        eventType = eventType,
        repeatCount = repeatCount,
        protocol = connectedDevice?.protocol ?: error("当前没有已连接的蓝牙设备"),
    )
}
```

- [ ] **Step 4: 在 `ProcessLiveEventUseCase.kt` 用事件类型调用 `enqueueWaveform`**

```kotlin
is OutputAction.BluetoothWaveformAction -> {
    bluetoothRepository.enqueueWaveform(
        waveformId = action.waveformId,
        eventType = event.type,
        repeatCount = repeatCount,
    )
}
```

- [ ] **Step 5: 运行仓库与用例测试确认通过**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.yokonex.bililive.data.bluetooth.DefaultBluetoothRepositoryTest" --tests "com.yokonex.bililive.domain.usecase.ProcessLiveEventUseCaseTest"`
Expected: PASS

- [ ] **Step 6: 提交仓库与用例改动**

```bash
git add app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothRepository.kt app/src/main/java/com/yokonex/bililive/data/bluetooth/DefaultBluetoothRepository.kt app/src/main/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCase.kt app/src/test/java/com/yokonex/bililive/data/bluetooth/DefaultBluetoothRepositoryTest.kt app/src/test/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCaseTest.kt
git commit -m "feat(bluetooth): 接入仓库级混波执行路径" -m "扩展蓝牙仓库接口，新增按事件类型入队混波的能力，并保留串行播放作为安全回退路径。" -m "让直播事件处理用例不再直接顺序播放整条波形，而是把蓝牙动作提交给统一的运行时执行。"
```

### Task 6: 扩展运行时状态和输出页展示

**Files:**
- Modify: `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/BluetoothRuntimeStatus.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/app/ui/output/OutputConfigViewModel.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/app/ui/output/OutputConfigScreen.kt`
- Modify: `app/src/test/java/com/yokonex/bililive/app/ui/output/OutputConfigViewModelTest.kt`

- [ ] **Step 1: 为输出页显示混波状态写失败测试**

```kotlin
@Test
fun state_includesMixModeLeaderAndCap() = runTest {
    runtimeStatus.value = BluetoothRuntimeStatus(
        connected = true,
        leaderEventType = LiveEventType.GIFT,
        activeLayerCount = 3,
        outputCap = 180,
        mixedChannelAStrength = 150,
        mixedChannelBStrength = 120,
        mixModeEnabled = true,
    )

    val state = viewModel.uiState.value
    assertEquals("礼物主层", state.bluetoothLeaderLabel)
    assertEquals("180", state.bluetoothOutputCapLabel)
}
```

- [ ] **Step 2: 扩展 `BluetoothRuntimeStatus.kt`，新增主层、活动层、上限和混波开关字段**

```kotlin
data class BluetoothRuntimeStatus(
    val connected: Boolean = false,
    val deviceName: String = "",
    val waveformName: String = "",
    val batteryLevel: Int? = null,
    val channelAStrength: Int = 0,
    val channelBStrength: Int = 0,
    val leaderEventType: LiveEventType? = null,
    val activeLayerCount: Int = 0,
    val outputCap: Int = 130,
    val mixedChannelAStrength: Int = 0,
    val mixedChannelBStrength: Int = 0,
    val mixModeEnabled: Boolean = false,
)
```

- [ ] **Step 3: 在 `OutputConfigViewModel.kt` 把新增运行时字段整理成 UI 状态**

```kotlin
bluetoothLeaderLabel = status.leaderEventType?.toDisplayLabel().orEmpty(),
bluetoothOutputCapLabel = status.outputCap.toString(),
bluetoothMixModeLabel = if (status.mixModeEnabled) "混波" else "串行",
activeBluetoothLayerCount = status.activeLayerCount,
channelAStrength = status.mixedChannelAStrength,
channelBStrength = status.mixedChannelBStrength,
```

- [ ] **Step 4: 在 `OutputConfigScreen.kt` 增加模式、主层、活动层和上限展示**

```kotlin
Text("执行模式 ${uiState.bluetoothMixModeLabel}")
Text("主层 ${uiState.bluetoothLeaderLabel.ifBlank { "无" }}")
Text("活动层 ${uiState.activeBluetoothLayerCount}")
Text("输出上限 ${uiState.bluetoothOutputCapLabel}")
```

- [ ] **Step 5: 运行输出页测试确认通过**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.yokonex.bililive.app.ui.output.OutputConfigViewModelTest"`
Expected: PASS

- [ ] **Step 6: 提交状态与 UI 改动**

```bash
git add app/src/main/java/com/yokonex/bililive/data/bluetooth/model/BluetoothRuntimeStatus.kt app/src/main/java/com/yokonex/bililive/app/ui/output/OutputConfigViewModel.kt app/src/main/java/com/yokonex/bililive/app/ui/output/OutputConfigScreen.kt app/src/test/java/com/yokonex/bililive/app/ui/output/OutputConfigViewModelTest.kt
git commit -m "feat(output): 展示蓝牙混波运行时状态" -m "扩展蓝牙运行时状态，补充主层事件、活动层数、输出上限与混波模式字段。" -m "让输出配置页可以直观看到混波是否启用、当前上限为何变化，以及实时 A/B 输出值。"
```

### Task 7: 全量回归与收尾

**Files:**
- Modify: `docs/superpowers/plans/2026-05-29-bluetooth-mixed-waveform-output.md`

- [ ] **Step 1: 运行蓝牙相关单测回归**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.yokonex.bililive.data.bluetooth.EmsProtocolEncoderTest" --tests "com.yokonex.bililive.data.bluetooth.BluetoothMixRuntimeTest" --tests "com.yokonex.bililive.data.bluetooth.DefaultBluetoothRepositoryTest" --tests "com.yokonex.bililive.domain.usecase.ProcessLiveEventUseCaseTest" --tests "com.yokonex.bililive.app.ui.output.OutputConfigViewModelTest"`
Expected: PASS

- [ ] **Step 2: 构建 debug 包验证编译通过**

Run: `.\gradlew.bat assembleDebug`
Expected: PASS

- [ ] **Step 3: 手动核对关键行为**

```text
1. 混波关闭时仍按旧逻辑串行播放
2. 礼物作为主层时输出上限升到 180
3. 只有弹幕/点赞时输出上限保持 130
4. ems_v1 在 A/B 同时输出时走 0x03 AB 通道
5. 活动层清空后只发送一次 stop packet
```

- [ ] **Step 4: 提交最终联调结果**

```bash
git add app/src/main/java app/src/test/java docs/superpowers/plans/2026-05-29-bluetooth-mixed-waveform-output.md
git commit -m "test(bluetooth): 完成混波输出回归验证" -m "回归协议编码、混波运行时、仓库接线、用例和输出页状态，确认多事件叠加输出与串行回退路径都可正常工作。" -m "补充最终联调记录，固定礼物主层上限、ems_v1 AB 映射和 stop 收尾行为。"
```
