# Bililive 安卓原生独立端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `D:\BiliLive-YOKONEX-App` 中落地一个可独立运行的原生安卓 App，接入第三方房间消息流，支持后台监听、蓝牙 EMS / WebSocket 双输出可切换，并迁移现有内置波形与 `ems_v1` / `ems_v2` 协议能力。

**Architecture:** 使用 `Kotlin + Jetpack Compose + Foreground Service` 搭建安卓原生单体应用，按 `app / domain / data / service / shared` 分层。先完成工程骨架、核心模型与存储，再分别接入第三方消息流、WebSocket、安卓 BLE，最后整合规则执行、后台保活与移动端页面流。

**Tech Stack:** Kotlin、Gradle Kotlin DSL、Jetpack Compose、Material 3、Navigation Compose、ViewModel、Coroutines、Flow、Room、DataStore、OkHttp、kotlinx.serialization、Hilt、JUnit、Turbine、MockK、Android BLE API

---

## 前置假设

1. 包名暂定为 `com.yokonex.bililive`。
2. 最低支持版本按 `Android 10 (API 29)` 设计。
3. 第三方房间消息流首版先按“无 Cookie 版本可工作”设计；如果联调阶段发现必须依赖额外鉴权，再在直播连接页扩展配置项。
4. 目录 `D:\BiliLive-YOKONEX-App` 当前还不是 git 仓库，因此计划将“初始化仓库”放进第一任务。

## 文件结构与职责

**工程与构建**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `gradle/libs.versions.toml`
- Create: `app/src/main/AndroidManifest.xml`

**应用入口与依赖注入**
- Create: `app/src/main/java/com/yokonex/bililive/BiliLiveApplication.kt`
- Create: `app/src/main/java/com/yokonex/bililive/MainActivity.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/navigation/AppNavGraph.kt`
- Create: `app/src/main/java/com/yokonex/bililive/di/AppModule.kt`
- Create: `app/src/main/java/com/yokonex/bililive/di/DataModule.kt`
- Create: `app/src/main/java/com/yokonex/bililive/di/ServiceModule.kt`

**领域模型与规则**
- Create: `app/src/main/java/com/yokonex/bililive/domain/model/LiveEvent.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/model/EventPayload.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/model/TriggerRule.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/model/OutputAction.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/model/WaveformDefinition.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/model/AppState.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/rule/RuleMatcher.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/usecase/StartMonitoringUseCase.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/usecase/StopMonitoringUseCase.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCase.kt`

**数据与存储**
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/AppDatabase.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/entity/RuleEntity.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/entity/WaveformEntity.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/entity/EventLogEntity.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/dao/RuleDao.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/dao/WaveformDao.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/dao/EventLogDao.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/SettingsStore.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/mapper/RuleMapper.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/mapper/WaveformMapper.kt`

**直播消息流**
- Create: `app/src/main/java/com/yokonex/bililive/data/live/ThirdPartyLiveGateway.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/live/ThirdPartyMessageParser.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/live/model/ThirdPartyMessage.kt`

**WebSocket 输出**
- Create: `app/src/main/java/com/yokonex/bililive/data/websocket/CommandSocketClient.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/websocket/CommandPayloadFactory.kt`

**蓝牙输出**
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothRepository.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothDeviceClassifier.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoder.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/EmsWaveformRuntime.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/AndroidBleManager.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/BluetoothDevice.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/BluetoothConnectionState.kt`

**服务与后台保活**
- Create: `app/src/main/java/com/yokonex/bililive/service/LiveMonitorService.kt`
- Create: `app/src/main/java/com/yokonex/bililive/service/ServiceCoordinator.kt`
- Create: `app/src/main/java/com/yokonex/bililive/service/NotificationFactory.kt`

**UI**
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/dashboard/DashboardScreen.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/dashboard/DashboardViewModel.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/live/LiveConfigScreen.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/live/LiveConfigViewModel.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/output/OutputConfigScreen.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/output/OutputConfigViewModel.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesScreen.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesViewModel.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/logs/LogsScreen.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/logs/LogsViewModel.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/components/StatusCard.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/components/EventLogItem.kt`

**资源**
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/drawable/ic_notification.xml`

**测试**
- Create: `app/src/test/java/com/yokonex/bililive/domain/rule/RuleMatcherTest.kt`
- Create: `app/src/test/java/com/yokonex/bililive/data/live/ThirdPartyMessageParserTest.kt`
- Create: `app/src/test/java/com/yokonex/bililive/data/websocket/CommandPayloadFactoryTest.kt`
- Create: `app/src/test/java/com/yokonex/bililive/data/bluetooth/BluetoothDeviceClassifierTest.kt`
- Create: `app/src/test/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoderTest.kt`
- Create: `app/src/test/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCaseTest.kt`
- Create: `app/src/test/java/com/yokonex/bililive/data/storage/SettingsStoreTest.kt`
- Create: `app/src/androidTest/java/com/yokonex/bililive/app/ui/dashboard/DashboardScreenTest.kt`

**参考文档**
- Spec: `docs/superpowers/specs/2026-05-27-bililive-android-native-design.md`

## 实施原则

1. 先建工程和测试骨架，再接入业务能力。
2. 每个行为先写失败测试，再写最小实现。
3. 领域模型优先保持小而稳定，不把 Android API 直接带进 domain 层。
4. 蓝牙协议与波形编码必须做成可单元测试的纯 Kotlin 逻辑。
5. 前台服务负责运行时，页面只负责展示和发出意图。
6. 每个任务结束后都做一次小范围验证并提交。

### Task 1: 初始化安卓工程、Gradle 和 git 基线

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `gradle/libs.versions.toml`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `.gitignore`

- [ ] **Step 1: 初始化 git 仓库并确认当前工作区干净**

Run: `git init`
Expected: 输出 `Initialized empty Git repository`

- [ ] **Step 2: 创建根级构建文件与版本目录**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BiliLive-YOKONEX-App"
include(":app")
```

- [ ] **Step 3: 在 `libs.versions.toml` 中声明 Compose、Hilt、Room、OkHttp、测试依赖**

```toml
[versions]
kotlin = "2.1.0"
agp = "8.8.0"
compose-bom = "2025.01.01"
```

- [ ] **Step 4: 写最小 `app/build.gradle.kts`，只接入能编译空壳 App 的依赖**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.yokonex.bililive"
    compileSdk = 35
}
```

- [ ] **Step 5: 写最小 Manifest，声明 Application、MainActivity 和前台服务权限占位**

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

- [ ] **Step 6: 运行 Gradle 基线检查**

Run: `.\gradlew.bat tasks`
Expected: PASS，能列出 Android/Gradle 任务且无语法错误

- [ ] **Step 7: Commit**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml app/build.gradle.kts app/proguard-rules.pro app/src/main/AndroidManifest.xml
git commit -m "chore(android): 初始化安卓工程与构建基线" -m "建立 Gradle Kotlin DSL、版本目录、Manifest 与基础权限声明，为后续 Kotlin Compose 开发提供可编译起点。"
```

### Task 2: 搭建 Application、Compose 入口和导航空壳

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/BiliLiveApplication.kt`
- Create: `app/src/main/java/com/yokonex/bililive/MainActivity.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/navigation/AppNavGraph.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/drawable/ic_notification.xml`

- [ ] **Step 1: 为主 Activity 和导航空壳写仪表测试骨架**

```kotlin
@Test
fun dashboardRoute_isShownOnLaunch() {
    composeTestRule.onNodeWithText("直播控制台").assertExists()
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat connectedDebugAndroidTest`
Expected: FAIL，提示 `MainActivity` 或目标文本不存在

- [ ] **Step 3: 创建 `BiliLiveApplication` 和空 `MainActivity`**

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppNavGraph() }
    }
}
```

- [ ] **Step 4: 写 `AppNavGraph`，先放置 5 个页面路由占位**

```kotlin
NavHost(navController, startDestination = "dashboard") {
    composable("dashboard") { Text("直播控制台") }
    composable("live") { Text("直播连接") }
    composable("output") { Text("输出配置") }
    composable("rules") { Text("规则配置") }
    composable("logs") { Text("事件日志") }
}
```

- [ ] **Step 5: 添加主题、字符串资源和通知图标占位**

```xml
<string name="app_name">BiliLive YOKONEX</string>
```

- [ ] **Step 6: 再次运行仪表测试确认通过**

Run: `.\gradlew.bat connectedDebugAndroidTest`
Expected: PASS，首页能显示“直播控制台”

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/BiliLiveApplication.kt app/src/main/java/com/yokonex/bililive/MainActivity.kt app/src/main/java/com/yokonex/bililive/app/navigation/AppNavGraph.kt app/src/main/res/values/strings.xml app/src/main/res/values/themes.xml app/src/main/res/drawable/ic_notification.xml app/src/androidTest/java/com/yokonex/bililive/app/ui/dashboard/DashboardScreenTest.kt
git commit -m "feat(app): 搭建 Compose 入口与导航空壳" -m "补充 Application、MainActivity、五页导航占位与基础资源，使安卓项目具备最小可运行界面。"
```

### Task 3: 建立领域模型、规则匹配器与内置波形测试

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/domain/model/LiveEvent.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/model/EventPayload.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/model/TriggerRule.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/model/OutputAction.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/model/WaveformDefinition.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/rule/RuleMatcher.kt`
- Create: `app/src/test/java/com/yokonex/bililive/domain/rule/RuleMatcherTest.kt`

- [ ] **Step 1: 为礼物区间、点赞倍数、弹幕关键词写失败测试**

```kotlin
@Test
fun giftRule_matchesWithinPriceRange() {
    val matched = RuleMatcher.matches(rule, event)
    assertThat(matched).isTrue()
}
```

- [ ] **Step 2: 为输出模式和动作绑定选择写失败测试**

```kotlin
@Test
fun rule_resolvesBluetoothAction_whenOutputModeIsBluetooth() {
    val action = RuleMatcher.resolveAction(rule, OutputMode.BLUETOOTH)
    assertThat(action).isInstanceOf(BluetoothWaveformAction::class.java)
}
```

- [ ] **Step 3: 运行单测确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "*RuleMatcherTest"`
Expected: FAIL，提示模型或匹配器尚未定义

- [ ] **Step 4: 创建最小领域模型**

```kotlin
enum class LiveEventType { GIFT, LIKE, DANMAKU, SYSTEM }
enum class OutputMode { BLUETOOTH, WEBSOCKET }
```

- [ ] **Step 5: 在 `WaveformDefinition` 中加入迁移现有内置波形所需字段**

```kotlin
data class WaveformStep(
    val durationMs: Int,
    val channelA: Int,
    val channelB: Int,
    val channelAMode: Int = 0x01,
    val channelAFrequency: Int = 10
)
```

- [ ] **Step 6: 实现 `RuleMatcher` 的最小条件判断与动作解析**

```kotlin
fun resolveAction(rule: TriggerRule, mode: OutputMode): OutputAction? =
    when (mode) {
        OutputMode.BLUETOOTH -> rule.actionBindings.bluetoothAction
        OutputMode.WEBSOCKET -> rule.actionBindings.websocketAction
    }
```

- [ ] **Step 7: 运行单测确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "*RuleMatcherTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/domain/model app/src/main/java/com/yokonex/bililive/domain/rule/RuleMatcher.kt app/src/test/java/com/yokonex/bililive/domain/rule/RuleMatcherTest.kt
git commit -m "feat(domain): 建立事件规则与动作模型" -m "定义 LiveEvent、TriggerRule、OutputAction、WaveformDefinition 与 RuleMatcher，为后续直播事件处理和输出路由提供稳定领域基础。"
```

### Task 4: 搭建 Room、DataStore 与默认波形写入逻辑

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/AppDatabase.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/entity/RuleEntity.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/entity/WaveformEntity.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/entity/EventLogEntity.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/dao/RuleDao.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/dao/WaveformDao.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/dao/EventLogDao.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/storage/SettingsStore.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/mapper/WaveformMapper.kt`
- Create: `app/src/test/java/com/yokonex/bililive/data/storage/SettingsStoreTest.kt`

- [ ] **Step 1: 为首次启动写入默认房间设置和内置波形写失败测试**

```kotlin
@Test
fun defaultWaveforms_areSeededOnFirstLaunch() = runTest {
    val waveforms = waveformDao.observeAll().first()
    assertThat(waveforms).isNotEmpty()
}
```

- [ ] **Step 2: 运行单测确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "*SettingsStoreTest"`
Expected: FAIL，提示数据库或设置存储未实现

- [ ] **Step 3: 创建 Room 实体和 DAO**

```kotlin
@Entity(tableName = "waveforms")
data class WaveformEntity(
    @PrimaryKey val id: String,
    val name: String,
    val builtin: Boolean,
    val payloadJson: String
)
```

- [ ] **Step 4: 实现 `SettingsStore`，保存房间号、输出模式和最近设备信息**

```kotlin
val roomId: Flow<String> = dataStore.data.map { it[ROOM_ID] ?: "" }
```

- [ ] **Step 5: 实现默认内置波形写入器，迁移当前桌面版内置波形集合**

```kotlin
if (waveformDao.count() == 0) {
    waveformDao.insertAll(DefaultWaveforms.all.map(WaveformMapper::toEntity))
}
```

- [ ] **Step 6: 运行存储测试确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "*SettingsStoreTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/data/storage app/src/main/java/com/yokonex/bililive/data/mapper/WaveformMapper.kt app/src/test/java/com/yokonex/bililive/data/storage/SettingsStoreTest.kt
git commit -m "feat(storage): 建立设置存储与默认波形落库" -m "补充 Room、DataStore 与默认内置波形写入逻辑，为规则、日志、蓝牙波形和运行状态持久化提供基础。"
```

### Task 5: 接入第三方房间消息流与解析测试

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/data/live/ThirdPartyLiveGateway.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/live/ThirdPartyMessageParser.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/live/model/ThirdPartyMessage.kt`
- Create: `app/src/test/java/com/yokonex/bililive/data/live/ThirdPartyMessageParserTest.kt`

- [ ] **Step 1: 为礼物、点赞、弹幕原始消息解析成统一事件写失败测试**

```kotlin
@Test
fun parser_mapsGiftMessageToLiveEvent() {
    val event = parser.parse(rawGiftJson)
    assertThat(event.type).isEqualTo(LiveEventType.GIFT)
}
```

- [ ] **Step 2: 运行单测确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ThirdPartyMessageParserTest"`
Expected: FAIL，提示解析器未实现

- [ ] **Step 3: 写最小解析模型，先只覆盖 MVP 所需字段**

```kotlin
@Serializable
data class ThirdPartyMessage(
    val cmd: String,
    val data: JsonObject? = null
)
```

- [ ] **Step 4: 实现消息解析器，把原始消息映射到 `LiveEvent`**

```kotlin
when (message.cmd) {
    "SEND_GIFT" -> mapGift(message)
    "LIKE_INFO_V3_CLICK" -> mapLike(message)
    "DANMU_MSG" -> mapDanmaku(message)
}
```

- [ ] **Step 5: 创建最小 `ThirdPartyLiveGateway`，先暴露 `Flow<LiveEvent>`**

```kotlin
interface ThirdPartyLiveGateway {
    fun events(roomId: String): Flow<LiveEvent>
}
```

- [ ] **Step 6: 运行单测确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ThirdPartyMessageParserTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/data/live app/src/test/java/com/yokonex/bililive/data/live/ThirdPartyMessageParserTest.kt
git commit -m "feat(live): 建立第三方房间消息流解析基线" -m "补充第三方原始消息模型与解析器，把礼物、点赞、弹幕事件统一映射为 LiveEvent。"
```

### Task 6: 接入 WebSocket 指令服务与命令载荷工厂

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/data/websocket/CommandSocketClient.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/websocket/CommandPayloadFactory.kt`
- Create: `app/src/test/java/com/yokonex/bililive/data/websocket/CommandPayloadFactoryTest.kt`

- [ ] **Step 1: 为 `command_one` 到 `command_ten` 载荷写失败测试**

```kotlin
@Test
fun payloadFactory_buildsSendCommandMessage() {
    val payload = factory.build("command_one")
    assertThat(payload).contains("sendCommand")
}
```

- [ ] **Step 2: 运行单测确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "*CommandPayloadFactoryTest"`
Expected: FAIL，提示工厂未实现

- [ ] **Step 3: 写 `CommandPayloadFactory`，对齐桌面版既有发送格式**

```kotlin
fun build(commandSlot: String): String = buildJsonObject {
    put("action", "sendCommand")
    put("commandId", commandSlot)
}.toString()
```

- [ ] **Step 4: 写最小 `CommandSocketClient`，支持登录、断开、发送和状态流**

```kotlin
interface CommandSocketClient {
    suspend fun connect(wsUrl: String, uid: String, token: String)
    suspend fun sendCommand(commandSlot: String)
}
```

- [ ] **Step 5: 运行单测确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "*CommandPayloadFactoryTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/data/websocket app/src/test/java/com/yokonex/bililive/data/websocket/CommandPayloadFactoryTest.kt
git commit -m "feat(websocket): 接入指令服务发送能力" -m "补充命令载荷工厂与 WebSocket 客户端接口，支持登录、状态跟踪和固定 command_slot 发送。"
```

### Task 7: 迁移蓝牙设备识别、两种协议封包与波形执行

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothDeviceClassifier.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoder.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/EmsWaveformRuntime.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/AndroidBleManager.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/BluetoothDevice.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/BluetoothConnectionState.kt`
- Create: `app/src/test/java/com/yokonex/bililive/data/bluetooth/BluetoothDeviceClassifierTest.kt`
- Create: `app/src/test/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoderTest.kt`

- [ ] **Step 1: 为 `ems_v1` / `ems_v2` 设备识别写失败测试**

```kotlin
@Test
fun classifier_detectsEmsV2FromNamePrefix() {
    val device = classifier.classify(name = "YYC-DJ-V2-001", serviceUuids = setOf(...))
    assertThat(device.protocol).isEqualTo("ems_v2")
}
```

- [ ] **Step 2: 为波形编码和 stop 包写失败测试**

```kotlin
@Test
fun encoder_buildsStopPacketForV1() {
    val packet = encoder.createStopPacket(protocol = "ems_v1")
    assertThat(packet).isEqualTo(byteArrayOf(...))
}
```

- [ ] **Step 3: 运行单测确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "*BluetoothDeviceClassifierTest" --tests "*EmsProtocolEncoderTest"`
Expected: FAIL，提示识别器和编码器未实现

- [ ] **Step 4: 把现有桌面版设备识别规则迁移到 `BluetoothDeviceClassifier`**

```kotlin
if (name.uppercase().startsWith("YYC-DJ-V2")) protocol = "ems_v2"
else if (name.uppercase().startsWith("YYC-DJ")) protocol = "ems_v1"
```

- [ ] **Step 5: 把现有 `create_waveform_packets` / `create_stop_packet` 迁移到 `EmsProtocolEncoder`**

```kotlin
fun createWaveformPackets(waveform: WaveformDefinition, protocol: String): List<ByteArray>
```

- [ ] **Step 6: 实现 `AndroidBleManager` 最小扫描、连接、写特征接口**

```kotlin
interface AndroidBleManager {
    suspend fun scan(): List<BluetoothDevice>
    suspend fun connect(deviceId: String)
    suspend fun write(packet: ByteArray)
}
```

- [ ] **Step 7: 实现 `EmsWaveformRuntime`，按分段发送波形并在结束后补 stop 包**

```kotlin
for (packet in packets) {
    bleManager.write(packet)
    delay(step.durationMs.toLong())
}
bleManager.write(stopPacket)
```

- [ ] **Step 8: 运行单测确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "*BluetoothDeviceClassifierTest" --tests "*EmsProtocolEncoderTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/data/bluetooth app/src/test/java/com/yokonex/bililive/data/bluetooth/BluetoothDeviceClassifierTest.kt app/src/test/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoderTest.kt
git commit -m "feat(bluetooth): 迁移 EMS 协议识别与波形执行核心" -m "补充两种 EMS 协议设备识别、波形编码、stop 包生成与安卓 BLE 运行时接口，为蓝牙输出提供可测试核心。"
```

### Task 8: 实现事件处理用例、前台服务与重连编排

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/domain/usecase/StartMonitoringUseCase.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/usecase/StopMonitoringUseCase.kt`
- Create: `app/src/main/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCase.kt`
- Create: `app/src/main/java/com/yokonex/bililive/service/LiveMonitorService.kt`
- Create: `app/src/main/java/com/yokonex/bililive/service/ServiceCoordinator.kt`
- Create: `app/src/main/java/com/yokonex/bililive/service/NotificationFactory.kt`
- Create: `app/src/test/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCaseTest.kt`

- [ ] **Step 1: 为命中规则后按输出模式路由动作写失败测试**

```kotlin
@Test
fun processLiveEvent_executesBluetoothAction_whenModeIsBluetooth() = runTest {
    useCase(event)
    verify { bluetoothRepository.playWaveform("soft_pulse") }
}
```

- [ ] **Step 2: 为“不因蓝牙失败而停止监听”写失败测试**

```kotlin
@Test
fun processLiveEvent_recordsFailure_withoutThrowing() = runTest {
    every { bluetoothRepository.playWaveform(any()) } throws IllegalStateException("boom")
    assertThatCode { runTest { useCase(event) } }.doesNotThrowAnyException()
}
```

- [ ] **Step 3: 运行单测确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ProcessLiveEventUseCaseTest"`
Expected: FAIL，提示用例和仓库编排未实现

- [ ] **Step 4: 实现 `ProcessLiveEventUseCase`，完成匹配、记录日志、执行动作、记录失败**

```kotlin
val action = RuleMatcher.resolveAction(rule, outputMode) ?: return
runCatching { executor.execute(action) }
    .onFailure { logRepository.recordFailure(...) }
```

- [ ] **Step 5: 实现 `ServiceCoordinator` 和 `LiveMonitorService`，建立前台服务状态机**

```kotlin
sealed interface ServiceStatus { data object Idle : ServiceStatus; data object Running : ServiceStatus }
```

- [ ] **Step 6: 在通知栏中显示房间号、输出模式和运行状态**

```kotlin
NotificationCompat.Builder(context, CHANNEL_ID)
    .setContentTitle("直播监听中")
    .setContentText("房间 $roomId · 模式 $outputMode")
```

- [ ] **Step 7: 运行单测确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ProcessLiveEventUseCaseTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/domain/usecase app/src/main/java/com/yokonex/bililive/service app/src/test/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCaseTest.kt
git commit -m "feat(service): 接入前台服务与事件处理主链路" -m "补充事件处理用例、日志回写、输出路由和前台服务状态机，使监听与执行逻辑脱离页面独立运行。"
```

### Task 9: 完成五个页面的 ViewModel 和交互流

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/dashboard/DashboardScreen.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/dashboard/DashboardViewModel.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/live/LiveConfigScreen.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/live/LiveConfigViewModel.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/output/OutputConfigScreen.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/output/OutputConfigViewModel.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesScreen.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesViewModel.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/logs/LogsScreen.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/logs/LogsViewModel.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/components/StatusCard.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/components/EventLogItem.kt`

- [ ] **Step 1: 为首页启停按钮、输出模式切换和最近事件列表写 UI 测试**

```kotlin
composeTestRule.onNodeWithText("启动监听").assertExists()
composeTestRule.onNodeWithText("输出模式").assertExists()
```

- [ ] **Step 2: 运行 UI 测试确认失败**

Run: `.\gradlew.bat connectedDebugAndroidTest`
Expected: FAIL，提示页面组件尚未实现

- [ ] **Step 3: 先实现 `DashboardScreen` 和 `DashboardViewModel`**

```kotlin
Button(onClick = onStartClick) { Text("启动监听") }
```

- [ ] **Step 4: 实现 `LiveConfigScreen` 与房间号、自动重连配置输入**

```kotlin
OutlinedTextField(value = uiState.roomId, onValueChange = viewModel::updateRoomId)
```

- [ ] **Step 5: 实现 `OutputConfigScreen`，展示蓝牙设备列表和 WebSocket 登录表单**

```kotlin
SegmentedButtonRow { ... }
```

- [ ] **Step 6: 实现 `RulesScreen` 和 `LogsScreen`，补规则列表与日志列表 UI**

```kotlin
LazyColumn { items(uiState.logs) { EventLogItem(it) } }
```

- [ ] **Step 7: 再次运行 UI 测试确认通过**

Run: `.\gradlew.bat connectedDebugAndroidTest`
Expected: PASS，五个页面的关键元素能显示

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/app/ui app/src/androidTest/java/com/yokonex/bililive/app/ui/dashboard/DashboardScreenTest.kt
git commit -m "feat(ui): 完成移动端五页主流程界面" -m "补充首页、直播连接、输出配置、规则配置和事件日志页面，以及对应 ViewModel 和基础组件。"
```

### Task 10: 权限引导、手动联调与全量回归

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/yokonex/bililive/MainActivity.kt`
- Modify: `docs/superpowers/plans/2026-05-27-bililive-android-native.md`

- [x] **Step 1: 补齐蓝牙、通知和前台服务相关权限**

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

- [x] **Step 2: 在 `MainActivity` 中增加首次权限引导流程**

```kotlin
launcher.launch(arrayOf(
    Manifest.permission.POST_NOTIFICATIONS,
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT
))
```

- [x] **Step 3: 运行全量单元测试**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 4: 运行关键仪表测试**

Run: `.\gradlew.bat connectedDebugAndroidTest`
Expected: PASS
Actual: 当前会话无已连接设备，执行结果为 `No connected devices!`

- [ ] **Step 5: 手动联调第三方房间消息流、WebSocket 和两种 EMS 设备**

Run: `.\gradlew.bat installDebug`
Expected: App 可安装到测试机，首页、后台服务、蓝牙与 WebSocket 主流程可操作
Actual: 当前会话无测试机连接，未执行真机安装与联调

- [ ] **Step 6: 手动验证锁屏 / 切后台后通知栏与监听状态**

Run: `adb shell dumpsys activity services | findstr LiveMonitorService`
Expected: 可看到前台服务仍在运行，通知栏状态正常
Actual: 依赖真机前台服务运行状态，当前会话未执行

- [x] **Step 7: 记录任何与 spec 不一致的实现偏差**

```markdown
- 如果第三方链路最终必须依赖额外鉴权参数，在此补充实际字段与 UI 调整原因
- 为兼容 Android 10/11 的蓝牙链路，额外声明了 `BLUETOOTH`、`BLUETOOTH_ADMIN` 和 `ACCESS_FINE_LOCATION(maxSdkVersion=30)`；同时对 `BLUETOOTH_SCAN` 增加了 `neverForLocation`
- 当前开发机默认 `JAVA_HOME` 仍指向 Java 8，Gradle 验证阶段需临时切换到 Java 21 才能运行 AGP 8.5.2
- `connectedDebugAndroidTest`、`installDebug` 和锁屏/后台前台服务验证仍需在接入真机后补齐
```

- [ ] **Step 8: 最终 Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/yokonex/bililive/MainActivity.kt docs/superpowers/plans/2026-05-27-bililive-android-native.md
git commit -m "feat(release): 完成安卓独立端 MVP 联调收口" -m "补齐权限引导、前台服务验证与全量回归说明，使第三方消息流、蓝牙 EMS 和 WebSocket 输出主流程达到可验收状态。"
```
