# Event Categorized Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让首页“最近事件”按礼物、点赞、弹幕分类展示，并让日志页支持按事件类型筛选。

**Architecture:** 保持现有事件日志存储和采集链路不动，只在 `DashboardViewModel` / `LogsViewModel` 做 UI 数据整形。首页把统一事件流切成三个分组列表，日志页保留时间流并加本地筛选状态。

**Tech Stack:** Kotlin、Jetpack Compose、ViewModel、StateFlow、JUnit4

---

### Task 1: 为首页分组和日志筛选补失败测试

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/app/src/test/java/com/yokonex/bililive/app/ui/dashboard/DashboardViewModelTest.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/test/java/com/yokonex/bililive/app/ui/logs/LogsViewModelTest.kt`

- [ ] **Step 1: 为首页分组列表写失败测试**

```kotlin
@Test
fun buildDashboardEventSections_groupsByGiftLikeDanmaku() {
    val sections = buildDashboardEventSections(logs)
    assertEquals(3, sections.size)
}
```

- [ ] **Step 2: 为日志筛选写失败测试**

```kotlin
@Test
fun filterLogs_returnsOnlySelectedEventType() {
    val filtered = filterLogs(logs, EventLogFilter.LIKE)
    assertTrue(filtered.all { it.eventType == "LIKE" })
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.yokonex.bililive.app.ui.dashboard.DashboardViewModelTest" --tests "com.yokonex.bililive.app.ui.logs.LogsViewModelTest"`
Expected: FAIL，提示新的分组/筛选函数未实现

### Task 2: 实现首页分类展示

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/dashboard/DashboardViewModel.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/dashboard/DashboardScreen.kt`

- [ ] **Step 1: 在 ViewModel 中把最近事件整形成礼物、点赞、弹幕三个分组**
- [ ] **Step 2: 在 Screen 中把“最近事件”改成三个分组卡片**
- [ ] **Step 3: 保持首页只展示每组最近 3-5 条，避免卡片过长**

### Task 3: 实现日志页类型筛选

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/logs/LogsViewModel.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/logs/LogsScreen.kt`

- [ ] **Step 1: 增加 `全部 / 礼物 / 点赞 / 弹幕 / 系统` 筛选状态**
- [ ] **Step 2: 用筛选后的列表驱动日志页统计和列表**
- [ ] **Step 3: 提供简单的点击切换 UI，不改变底层日志存储**

### Task 4: 运行验证

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/docs/superpowers/plans/2026-05-28-event-categorized-display.md`

- [ ] **Step 1: 重新运行首页和日志页测试**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.yokonex.bililive.app.ui.dashboard.DashboardViewModelTest" --tests "com.yokonex.bililive.app.ui.logs.LogsViewModelTest"`
Expected: PASS

- [ ] **Step 2: 构建 debug APK**

Run: `./gradlew.bat assembleDebug`
Expected: PASS
