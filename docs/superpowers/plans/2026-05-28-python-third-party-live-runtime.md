# Python 第三方直播采集 Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在安卓 App 中嵌入 Python runtime，并让第三方直播采集按 `D:\BiliLive-YOKONEX` 的 `bilibili_api.LiveDanmaku` 实现跑起来，同时保持 Kotlin 侧继续负责服务生命周期、UI 和规则执行。

**Architecture:** 通过 `Chaquopy` 把 `bilibili_api` 和一层轻量 Python facade 打进 `app` 模块；Python 侧负责 `LiveDanmaku` 连接、事件注册、事件映射和内存队列，Kotlin 侧新增 `PythonBackedThirdPartyLiveGateway` 轮询拉取事件，并继续复用现有 `ServiceCoordinator`、`ProcessLiveEventUseCase` 和 UI 状态链路。

**Tech Stack:** Kotlin、Coroutines、Flow、kotlinx.serialization、Chaquopy、Python 3.11、bilibili_api、aiohttp、JUnit4

---

## 文件结构与职责

**Gradle / 构建**
- Modify: `D:/BiliLive-YOKONEX-App/build.gradle.kts`
- Modify: `D:/BiliLive-YOKONEX-App/gradle/libs.versions.toml`
- Modify: `D:/BiliLive-YOKONEX-App/app/build.gradle.kts`

**Kotlin 直播采集桥**
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/AppContainer.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/live/ThirdPartyMessageParser.kt`
- Create: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/live/PythonThirdPartyBridge.kt`
- Create: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/live/PythonBackedThirdPartyLiveGateway.kt`

**Python facade**
- Create: `D:/BiliLive-YOKONEX-App/app/src/main/python/live_runtime/__init__.py`
- Create: `D:/BiliLive-YOKONEX-App/app/src/main/python/live_runtime/third_party_runtime.py`

**测试**
- Modify: `D:/BiliLive-YOKONEX-App/app/src/test/java/com/yokonex/bililive/data/live/ThirdPartyMessageParserTest.kt`
- Create: `D:/BiliLive-YOKONEX-App/app/src/test/java/com/yokonex/bililive/data/live/PythonBackedThirdPartyLiveGatewayTest.kt`

## Task 1: 先补 Kotlin 侧回归测试，锁定 Python 映射格式

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/app/src/test/java/com/yokonex/bililive/data/live/ThirdPartyMessageParserTest.kt`
- Create: `D:/BiliLive-YOKONEX-App/app/src/test/java/com/yokonex/bililive/data/live/PythonBackedThirdPartyLiveGatewayTest.kt`

- [ ] **Step 1: 为参考项目 `event_mapper.py` 输出格式写失败测试**

```kotlin
@Test
fun parse_mappedGiftEvent_mapsToLiveEvent() {
    val event = parser.parse(
        """{"event_type":"gift","cmd":"SEND_GIFT","room_id":22608112,"uname":"测试用户","timestamp":1714113037,"payload":{"gift_name":"辣条","gift_num":2,"price":100,"r_price":200}}""",
    )
    assertEquals(LiveEventType.GIFT, event.type)
}
```

- [ ] **Step 2: 为 Python 队列轮询网关写失败测试**

```kotlin
@Test
fun events_drainsBridgeQueueIntoFlow() = runTest {
    val gateway = PythonBackedThirdPartyLiveGateway(...)
    val event = gateway.events("22608112").first()
    assertEquals(LiveEventType.DANMAKU, event.type)
}
```

- [ ] **Step 3: 运行失败测试确认当前行为还不支持 Python facade**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.yokonex.bililive.data.live.ThirdPartyMessageParserTest" --tests "com.yokonex.bililive.data.live.PythonBackedThirdPartyLiveGatewayTest"`
Expected: FAIL，提示尚不支持映射后事件格式和 Python 网关

## Task 2: 接入 Chaquopy 与 Python facade

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/build.gradle.kts`
- Modify: `D:/BiliLive-YOKONEX-App/gradle/libs.versions.toml`
- Modify: `D:/BiliLive-YOKONEX-App/app/build.gradle.kts`
- Create: `D:/BiliLive-YOKONEX-App/app/src/main/python/live_runtime/__init__.py`
- Create: `D:/BiliLive-YOKONEX-App/app/src/main/python/live_runtime/third_party_runtime.py`

- [ ] **Step 1: 给根工程和 app 模块接入 Chaquopy 插件**
- [ ] **Step 2: 固定 Python 3.11 和 `arm64-v8a`，把 `buildPython` 指向本机 3.11**
- [ ] **Step 3: 通过 `pip` 打入 `bilibili_api` 与 `aiohttp`**
- [ ] **Step 4: 编写 Python facade，复刻参考项目 `ws_client.py` / `event_mapper.py` 的注册事件、客户端选择、事件映射和内存队列**

## Task 3: 接 Kotlin 侧 Python 网关并切到 debug 构建

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/AppContainer.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/live/ThirdPartyMessageParser.kt`
- Create: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/live/PythonThirdPartyBridge.kt`
- Create: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/live/PythonBackedThirdPartyLiveGateway.kt`

- [ ] **Step 1: 实现 Python bridge 接口和 Chaquopy 实现**
- [ ] **Step 2: 让 `ThirdPartyMessageParser` 同时支持原始 bilibili 消息和 Python facade 的映射消息**
- [ ] **Step 3: 实现 `PythonBackedThirdPartyLiveGateway`，负责 start / drain / status / stop**
- [ ] **Step 4: 在 `AppContainer` 中让 debug 构建优先使用 Python 采集网关**

## Task 4: 验证单测与 debug 构建

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/docs/superpowers/plans/2026-05-28-python-third-party-live-runtime.md`

- [ ] **Step 1: 重新运行直播采集相关单测**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.yokonex.bililive.data.live.*" --tests "com.yokonex.bililive.service.ServiceCoordinatorTest" --tests "com.yokonex.bililive.domain.usecase.ProcessLiveEventUseCaseTest"`
Expected: PASS

- [ ] **Step 2: 构建 debug APK，验证 Chaquopy 依赖能实际打包**

Run: `./gradlew.bat assembleDebug`
Expected: PASS，产出包含 Python runtime 的 `app-debug.apk`

- [ ] **Step 3: 记录任何包体、依赖兼容或构建环境偏差**

```markdown
- 如果 `bilibili_api` 某个依赖在 Android wheel 上不可用，需要记录具体 pip 错误并替换版本或补本地 facade。
- 如果构建机 Python 版本不匹配，需要记录实际使用的 `buildPython` 路径。
```
