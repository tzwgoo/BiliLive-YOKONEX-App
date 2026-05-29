# 安卓蓝牙混合波形输出设计

> 日期：2026-05-29
> 主题：为 `D:\BiliLive-YOKONEX-App` 设计蓝牙模式下的多事件混合波形输出能力

## 背景

当前安卓 App 已具备以下蓝牙输出基础能力：

1. [ProcessLiveEventUseCase.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCase.kt) 会在规则命中后直接执行蓝牙动作。
2. [DefaultBluetoothRepository.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/bluetooth/DefaultBluetoothRepository.kt) 当前通过 `playWaveform()` 顺序播放整条波形。
3. [EmsWaveformRuntime.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/bluetooth/EmsWaveformRuntime.kt) 按 `step -> write packet -> delay` 的方式串行输出。
4. [EmsProtocolEncoder.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/bluetooth/EmsProtocolEncoder.kt) 已支持 `ems_v1` 和 `ems_v2` 两种协议封包。
5. [WaveformDefinition.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/domain/model/WaveformDefinition.kt) 已定义波形、分段和信号模式。

但当前实现存在两个明显限制：

1. 单个事件只会取第一条命中规则，不存在多规则并行输出。
2. 蓝牙执行链路按整条波形串行播放，多个事件同时触发时只能排队，无法形成叠加体感。

本次目标不是改造规则系统去“一次触发多条规则”，而是在蓝牙执行层支持“多个事件波形同时参与输出”的混波能力。

## 用户确认约束

本次设计已确认以下业务边界：

1. 目标能力是“叠加混波”，不是纯排队串播，也不是简单抢占覆盖。
2. 次层参与规则采用按事件类型定权，而不是全局统一权重或按规则逐条配置。
3. 事件类型权重为：
   - 礼物 `100%`
   - 弹幕 `40%`
   - 点赞 `20%`
4. 设备物理最大输出为 `180`。
5. 软件混波上限采用分层限制：
   - 普通混波上限 `130`
   - 当礼物事件为主层时允许提升到 `180`
6. `ems_v1` 与 `ems_v2` 都应视为双通道设备，但协议表达能力不同。

## 目标

1. 蓝牙模式下允许多个事件同时参与波形输出。
2. 对同一时刻的多个活动波形做时间片合成，而不是排队等待整条波形播完。
3. 让礼物事件在混波中保持最高优先级和更高输出上限。
4. 同时兼容 `ems_v1` 与 `ems_v2` 两种设备协议。
5. 保留可回退到当前串行播放模型的安全开关。

## 非目标

1. 不在本次设计中改造规则引擎为“一次事件触发多条规则”。
2. 不开放 UI 上的混波参数编辑页。
3. 不引入按规则自定义权重、优先级或抢占策略。
4. 不实现自动学习、智能压缩或基于历史事件的动态权重调整。
5. 不修改波形编辑器的数据结构。
6. 不在首版实现中混合 `mode / frequency / pulseWidth` 等协议参数，只混合强度。

## 现状与复用点

### 事件处理链路

1. [ServiceCoordinator.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/service/ServiceCoordinator.kt) 当前串行消费事件流。
2. [ProcessLiveEventUseCase.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/domain/usecase/ProcessLiveEventUseCase.kt) 负责规则命中、冷却判断和动作执行。
3. 蓝牙动作当前只有“播放某个波形若干次”这一种运行时语义。

### 蓝牙输出链路

1. [BluetoothRepository.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothRepository.kt) 当前暴露 `playWaveform()` 接口。
2. [DefaultBluetoothRepository.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/bluetooth/DefaultBluetoothRepository.kt) 已具备：
   - 设备连接状态管理
   - 波形查找
   - 运行时状态广播
3. [BluetoothRuntimeStatus.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/bluetooth/model/BluetoothRuntimeStatus.kt) 已承载基础运行态展示。

### 协议能力差异

1. `ems_v2` 当前编码模型已具备独立 `A/B` 两路参数。
2. `ems_v1` 虽然也是双通道设备，但根据协议说明：
   - 通道号可指定 `A / B / AB`
   - 强度、模式、频率、脉冲时间只有一组
   - `AB` 同步输出时应使用 `0x03`
3. 因此：
   - `ems_v2` 属于独立双通道模型
   - `ems_v1` 属于同步双通道模型

## 核心设计结论

本次采用“实时混波调度器”方案，而不是并发写多个波形、也不是动态重生成整条合成波形。

设计原则：

1. 每个事件被视为一个活动波形层。
2. 运行时按固定时间片循环计算当前输出帧。
3. 每个时间片分别读取所有活动层在“当前时刻”的波形分段。
4. 主层按 `100%` 参与输出，次层按事件类型权重参与输出。
5. `A/B` 通道分别计算混合强度。
6. 协议参数由主层当前分段提供。
7. 活动层全部结束后统一发送停止指令。

## 架构设计

建议在蓝牙模块新增以下边界：

### 1. `BluetoothMixRuntime`

位置建议：

1. `app/src/main/java/com/yokonex/bililive/data/bluetooth/BluetoothMixRuntime.kt`

职责：

1. 管理活动波形层集合。
2. 驱动固定时间片的运行循环。
3. 每个 tick 计算当前混合输出帧。
4. 调用协议编码器生成单帧包并写入 BLE。
5. 在活动层清空后发 stop packet。

### 2. `ActiveWaveformLayer`

位置建议：

1. `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/ActiveWaveformLayer.kt`

职责：

1. 描述一个参与混波的事件实例。
2. 保存事件类型、波形定义、开始时间、repeat 次数、优先级和权重。
3. 提供“当前时间点落在哪个分段”的查询能力。

### 3. `MixFrame`

位置建议：

1. `app/src/main/java/com/yokonex/bililive/data/bluetooth/model/MixFrame.kt`

职责：

1. 表示某一个时间片最终要下发的输出帧。
2. 包含 `channelA / channelB` 最终强度。
3. 包含当前输出所采用的模式参数。
4. 包含本帧上限 `cap` 和主层事件类型。

### 4. `MixPolicy`

位置建议：

1. `app/src/main/java/com/yokonex/bililive/data/bluetooth/MixPolicy.kt`

职责：

1. 集中存放权重、优先级、层数上限、tick 时长和上限规则。
2. 避免把业务常量散落在运行时逻辑中。

## 数据模型设计

### 活动层模型

建议最少包含以下字段：

1. `id`
2. `eventType`
3. `waveform`
4. `startedAtElapsedMs`
5. `repeatCount`
6. `priority`
7. `weight`

### 输出帧模型

建议最少包含以下字段：

1. `channelA`
2. `channelB`
3. `channelAMode`
4. `channelAFrequency`
5. `channelAPulseWidth`
6. `channelBMode`
7. `channelBFrequency`
8. `channelBPulseWidth`
9. `cap`
10. `leaderEventType`

### 运行时状态扩展

建议扩展 [BluetoothRuntimeStatus.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/bluetooth/model/BluetoothRuntimeStatus.kt)：

1. `leaderEventType`
2. `activeLayerCount`
3. `outputCap`
4. `mixedChannelAStrength`
5. `mixedChannelBStrength`
6. `mixModeEnabled`

这样输出页和调试信息才能准确反映：

1. 当前是否在混波模式。
2. 当前主层是否为礼物。
3. 当前上限是 `130` 还是 `180`。

## 时间片调度设计

### Tick 频率

首版建议：

1. `tickMs = 50`

原因：

1. 足够接近实时体感。
2. 不至于让 BLE 写入频率过高。
3. 更容易在两种协议下稳定验证。

### 活动层推进

每个活动层在加入时记录 `startedAtElapsedMs`。

每个 tick 计算：

1. `elapsed = now - startedAtElapsedMs`
2. 根据 `repeatCount` 和整条波形总时长确定是否已结束。
3. 若未结束，则在当前轮次内根据累计时长定位到当前 `step`。

这里不预展开成固定采样点数组，而是按累计时长定位当前 step，避免无意义内存放大。

### 主层选择

每个 tick 都重新选择主层，而不是仅在入队时选择一次。

优先级固定为：

1. 礼物
2. 弹幕
3. 点赞

同优先级时采用先到先服务，避免主层频繁抖动。

### 次层参与规则

主层：

1. 权重固定 `1.0`

次层：

1. 礼物 `1.0`
2. 弹幕 `0.4`
3. 点赞 `0.2`

### 强度合成公式

对 `A/B` 通道分别计算：

```text
cap = if (leaderEventType == GIFT) 180 else 130

mixedA = leaderA * 1.0 + sum(followerA * weight)
mixedB = leaderB * 1.0 + sum(followerB * weight)

outputA = clamp(round(mixedA), 0, cap)
outputB = clamp(round(mixedB), 0, cap)
```

说明：

1. 混波上限只由当前主层是否为礼物决定。
2. 弹幕、点赞即使叠很多层，也不能把上限抬到 `180`。
3. 礼物结束后若只剩弹幕/点赞，下一 tick 立刻回落到 `130` 上限。

## 协议编码设计

### `ems_v2`

`ems_v2` 走独立双通道路径：

1. `channelA` 和 `channelB` 直接采用混波后的最终强度。
2. 模式参数沿用主层当前 step 的 `A/B` 参数。
3. 新增单帧编码入口供 `BluetoothMixRuntime` 使用。

### `ems_v1`

`ems_v1` 走同步双通道映射路径。

由于协议中：

1. `A/B/AB` 是目标通道选择。
2. 强度和模式参数只有一组。
3. `AB` 同步输出要求使用 `0x03`。

因此第一版不伪造独立双路参数，而是按以下规则降维：

1. 若 `A > 0` 且 `B == 0`，发送 `A` 通道指令。
2. 若 `B > 0` 且 `A == 0`，发送 `B` 通道指令。
3. 若 `A > 0` 且 `B > 0`，发送 `AB` 通道指令。
4. 若 `A == 0` 且 `B == 0`，视为停止输出。

当发送 `AB` 通道指令时：

1. 强度取 `max(A, B)`。
2. 模式、频率、脉冲时间取主层当前 step 的对应参数。

此设计目标是：

1. 保留 `ems_v1` 的双通道同步体感。
2. 不违背协议要求去做高频 `A/B` 交替发送。
3. 不让运行时对 `ems_v1` 做超出协议语义的伪独立控制。

### 编码器接口演进

建议保留现有整条波形编码函数，同时新增单帧入口：

1. 保留 `createWaveformPackets()`
2. 新增 `createStepPacket(...)`
3. 保留 `createStopPacket()`

这样可以：

1. 保持现有串行播放逻辑和已有测试继续可用。
2. 为混波运行时提供独立入口。

## 仓库与接口改造

### `BluetoothRepository`

当前接口过于偏向“整条波形播放”，建议演进为两层能力：

1. 保留串行播放能力，便于回退。
2. 新增混波事件入队能力。

建议增加：

1. `enqueueWaveform(waveformId, eventType, repeatCount)`
2. `clearActiveWaveforms()`
3. `setMixModeEnabled(enabled)`

### `DefaultBluetoothRepository`

职责保留：

1. 连接管理
2. 波形查找
3. 运行状态广播

新增职责：

1. 根据当前运行模式决定走串行播放还是混波入队。
2. 将波形定义和事件类型交给 `BluetoothMixRuntime`。
3. 在断连时清空活动层。

### `ProcessLiveEventUseCase`

当前蓝牙执行语义是：

1. 命中规则
2. 直接 `playWaveform()`

建议调整为：

1. 命中规则
2. 解析出 `event.type`
3. 调用 `BluetoothRepository.enqueueWaveform(...)`

这样用例层只关心“提交蓝牙事件”，不关心底层到底是串行播放还是混波调度。

## 运行时生命周期设计

### 入队

事件命中蓝牙动作后：

1. 查找波形定义
2. 构造活动层
3. 放入活动层列表
4. 若 tick loop 尚未运行，则启动 loop

### 主层切换

主层切换不做 stop/restart，而是在下一 tick 自然接管。

效果：

1. 礼物进来时，下一帧即可接任主层并提升上限到 `180`
2. 礼物结束后，下一帧即可回落到 `130`
3. 不会因为主层变化出现额外停顿

### 结束

活动层在以下场景结束：

1. 当前波形完整播完并完成 repeatCount
2. 蓝牙断连
3. 用户主动停止监听
4. 被层数上限策略淘汰

当活动层全部清空后：

1. 发送一次 stop packet
2. 更新运行时状态为输出归零
3. 停止 tick loop

## 保护与回退策略

### 层数上限

首版建议：

1. 最多保留 `4` 层活动层

超限时：

1. 若新事件优先级低于当前最低层，则直接丢弃新事件
2. 若新事件优先级更高，则淘汰最低优先级且最近加入的一层

### 断连清空

蓝牙断连时必须：

1. 停止 tick loop
2. 清空全部活动层
3. 重置运行状态

### 模式开关

建议新增运行策略开关：

1. `SERIAL`
2. `MIXED`

必要时可进一步细分为：

1. `SERIAL`
2. `MIXED_V2_ONLY`
3. `MIXED_ALL`

首版推荐至少保留 `SERIAL / MIXED` 两档，便于真机回退。

## UI 与观测性设计

本次不新增复杂配置 UI，但建议最小扩展输出页状态展示：

1. 当前运行模式：串行 / 混波
2. 当前活动层数
3. 当前主层事件类型
4. 当前输出上限
5. 当前混合后的 `A/B` 输出值

这样用户才能理解：

1. 为什么某一刻输出能冲到 `180`
2. 为什么礼物结束后又降回 `130`
3. 当前是不是有多个事件正在同时参与

## 测试策略

### 纯 Kotlin 单测

重点验证混波核心算法：

1. 单层输出与原始波形一致
2. 礼物主层时 `cap = 180`
3. 非礼物主层时 `cap = 130`
4. 弹幕按 `40%` 混入
5. 点赞按 `20%` 混入
6. 多层叠加后正确限幅
7. 主层切换后下一 tick 立即生效
8. 所有层结束后输出 stop

### 协议编码单测

1. `ems_v2` 单帧编码正确反映独立 `A/B`
2. `ems_v1` 在 `A/B/AB` 三种情况下通道号正确
3. `ems_v1` 在 `AB` 情况下强度取 `max(A, B)`
4. stop packet 行为保持与现有逻辑一致

### 仓库与运行时集成测试

使用 fake `AndroidBleManager`：

1. 入队首个事件后会启动 tick loop
2. 多事件同时存在时会持续产出多帧写包
3. 活动层清空后只发送一次 stop
4. 断连会终止 loop 并清空状态

## 风险与取舍

1. 最大风险在于 `50ms` tick 下真机 BLE 写入稳定性。
2. 若个别设备在该频率下不稳定，可优先调大 tick，而不是先改混波模型。
3. `ems_v1` 只能做同步双通道映射，无法实现 `ems_v2` 那种真正独立双通道细节。
4. 首版只混强度、不混模式参数，是有意的保守取舍，用来换取协议稳定性和实现确定性。

## 实现建议顺序

1. 为 `EmsProtocolEncoder` 补单帧编码能力与测试
2. 新增 `MixPolicy / ActiveWaveformLayer / MixFrame`
3. 实现 `BluetoothMixRuntime` 与算法测试
4. 扩展 `BluetoothRepository / DefaultBluetoothRepository`
5. 改造 `ProcessLiveEventUseCase` 接入混波入队
6. 扩展 `BluetoothRuntimeStatus` 和输出页展示
7. 最后接入运行模式开关与真机回退策略

## 成功标准

满足以下条件即可视为本次设计达标：

1. 蓝牙模式下多个事件可同时参与输出，而不是只排队等待。
2. 礼物事件在混波中始终具备最高优先级。
3. 普通混波不超过 `130`，礼物主层时允许到 `180`。
4. `ems_v2` 能保留独立 `A/B` 双通道混波。
5. `ems_v1` 能以协议允许的 `A/B/AB` 方式稳定输出。
6. 所有活动层结束后能正确停止输出。
7. 运行时可通过开关回退到当前串行播放模型。
