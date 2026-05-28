# Gift Trigger Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把礼物规则改成“单个礼物价值决定档位”，并新增全局选项控制礼物是“按数量触发”还是“不管数量只触发一次”。

**Architecture:** 新增全局 `GiftTriggerMode` 设置并持久化到 `SettingsStore`，由 `ProcessLiveEventUseCase` 在命中礼物规则后读取。规则匹配改用礼物单价 `price`，执行器接收 `repeatCount` 以决定蓝牙波形和 WebSocket 指令的触发次数。

**Tech Stack:** Kotlin、StateFlow、DataStore、Compose、JUnit4

---

### Task 1: 先补失败测试

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/app/src/test/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCaseTest.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/test/java/com/yokonex/bililive/data/storage/SettingsStoreTest.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/test/java/com/yokonex/bililive/app/ui/live/LiveConfigViewModelTest.kt`

- [ ] **Step 1: 为礼物按数量触发和单次触发写失败测试**
- [ ] **Step 2: 为礼物档位按单价匹配写失败测试**
- [ ] **Step 3: 为全局设置持久化和直播配置页选项写失败测试**
- [ ] **Step 4: 跑测试确认失败**

### Task 2: 实现主链路

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/domain/model/TriggerRule.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/domain/rule/RuleMatcher.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCase.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothRepository.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/bluetooth/DefaultBluetoothRepository.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/websocket/CommandSocketClient.kt`

- [ ] **Step 1: 新增 `GiftTriggerMode` 与 provider**
- [ ] **Step 2: 礼物规则改按 `payload.price` 匹配档位**
- [ ] **Step 3: 命中礼物后根据全局模式计算 `repeatCount`**
- [ ] **Step 4: 蓝牙与 WebSocket 执行器支持重复次数**

### Task 3: 实现全局设置和直播配置页

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/storage/SettingsStore.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/AppContainer.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/live/LiveConfigViewModel.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/live/LiveConfigScreen.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesViewModel.kt`
- Modify: `D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesScreen.kt`

- [ ] **Step 1: DataStore 增加礼物触发模式读写**
- [ ] **Step 2: 直播配置页增加全局选项**
- [ ] **Step 3: 规则页礼物文案改成“单个礼物价值”**

### Task 4: 回归验证

**Files:**
- Modify: `D:/BiliLive-YOKONEX-App/docs/superpowers/plans/2026-05-28-gift-trigger-mode.md`

- [ ] **Step 1: 跑相关单测**
- [ ] **Step 2: 运行 `assembleDebug`**
