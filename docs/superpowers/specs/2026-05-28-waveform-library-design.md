# 安卓波形库与可视化编辑器设计

> 日期：2026-05-28
> 主题：为 `D:\BiliLive-YOKONEX-App` 新增独立波形库菜单，并实现可视化波形管理与编辑能力

## 背景

当前安卓 App 已具备以下波形相关基础能力：

1. 已有 [WaveformDefinition.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/domain/model/WaveformDefinition.kt) 和 `WaveformStep` 领域模型。
2. 已有 [JsonWaveformDao.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/storage/JsonWaveformDao.kt) 做 UTF-8 JSON 持久化。
3. 已有 [DefaultBluetoothRepository.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/bluetooth/DefaultBluetoothRepository.kt) 和 `EmsWaveformRuntime` 承接波形执行。
4. 已有 [RulesViewModel.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesViewModel.kt) 在规则页中提供波形下拉选择。

但当前仍缺少独立的“波形库”入口，以及对波形进行新建、复制、删除、可视化编辑和规则绑定保护的完整管理流程。

参考项目 `D:\BiliLive-YOKONEX` 已有完整的“波形与事件规则”页，支持波形库、分段编辑、复制、新建、删除和规则引用保护。本次目标是在保持安卓原生 Material 3 风格的前提下，迁移这套波形管理工作流。

## 目标

1. 在底部导航新增独立“波形库”菜单。
2. 在波形库页展示全部内置波形和自定义波形。
3. 支持新建空白自定义波形。
4. 支持从内置或自定义波形复制为新的自定义波形。
5. 支持通过可视化拖拽直接编辑每个分段的 A/B 强度。
6. 支持在可视化编辑器中直接新增分段。
7. 支持通过分段明细区精确修改 `durationMs / channelA / channelB`。
8. 支持复制分段、删除分段，并保证至少保留 1 个分段。
9. 支持删除未被规则引用的自定义波形。
10. 在规则页继续使用同一份波形数据源，自动联动新增、删除和修改结果。

## 非目标

1. 不在编辑过程中实时下发波形到蓝牙设备。
2. 不提供波形试听或播放预览按钮。
3. 不开放底层协议字段编辑：
   - `channelAMode / channelAFrequency / channelAPulseWidth`
   - `channelBMode / channelBFrequency / channelBPulseWidth`
   - `executionMode / loopCount / signalMode`
4. 不实现撤销 / 重做。
5. 不实现批量导入导出。
6. 不实现自由手绘曲线自动离散成分段。
7. 不额外做平板双栏或横屏专门适配。

## 用户确认约束

1. 首版采用完整迁移范围，而不是轻量版。
2. “波形库”作为底部导航第 6 个独立菜单存在。
3. 首版以可视化拖拽为主，允许通过可视化交互直接新增分段。
4. 内置波形只读，必须复制为自定义后才能编辑。

## 现状与复用点

### 导航与页面结构

1. [AppNavGraph.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/navigation/AppNavGraph.kt) 当前维护 5 个底部菜单。
2. 现有页面普遍采用单页 Compose `LazyColumn` 结构，如：
   - [OutputConfigScreen.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/output/OutputConfigScreen.kt)
   - [RulesScreen.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesScreen.kt)

### 波形模型与持久化

1. [WaveformDefinition.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/domain/model/WaveformDefinition.kt) 已覆盖本次所需主模型。
2. [WaveformMapper.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/mapper/WaveformMapper.kt) 已支持领域模型与存储实体互转。
3. [WaveformEntity.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/storage/entity/WaveformEntity.kt) 与 [JsonWaveformDao.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/storage/JsonWaveformDao.kt) 已支持 JSON 存储。
4. [AppContainer.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/AppContainer.kt) 已将 `waveforms.json` 接入应用容器。

### 规则联动

1. [JsonRuleStore.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/storage/JsonRuleStore.kt) 已持久化规则。
2. [RulesViewModel.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesViewModel.kt) 已从 `WaveformDao.observeAll()` 订阅波形选项。
3. 删除波形时可直接复用规则数据做引用检查，无需另建引用表。

## 信息架构

新增一个底部导航项：

1. `route = "waveforms"`
2. `label = "波形库"`
3. 图标仍沿用当前文字占位风格，可先使用单字 `形`

进入后为单页工作区，按手机竖屏组织为上下布局：

1. 顶部说明区
   - 标题“波形库”
   - 内置/自定义说明
   - 当前编辑状态说明
   - 未保存提示
2. 波形库列表区
   - 展示全部波形卡片
   - 每张卡片包含名称、标签、分段数、总时长、最大强度、迷你预览
3. 可视化编辑区
   - `Canvas` 渲染波形
   - A/B 手柄拖拽
   - 分段边界插入点
4. 编辑操作区
   - 波形名称
   - 统计信息
   - 新建、复制、保存、删除
   - 分段精修列表

规则页不并入波形库页，继续保留原职责，只共享底层波形数据源。

## 数据模型设计

### 主模型保持不变

继续使用当前领域模型：

1. `WaveformDefinition`
   - `id`
   - `name`
   - `builtin`
   - `steps`
   - `executionMode`
   - `loopCount`
   - `signalMode`
2. `WaveformStep`
   - `durationMs`
   - `channelA`
   - `channelAMode`
   - `channelAFrequency`
   - `channelAPulseWidth`
   - `channelB`
   - `channelBMode`
   - `channelBFrequency`
   - `channelBPulseWidth`

### 首版允许编辑字段

只开放以下字段：

1. `WaveformDefinition.name`
2. `WaveformStep.durationMs`
3. `WaveformStep.channelA`
4. `WaveformStep.channelB`

其他字段策略：

1. 新建空白波形时使用现有默认值。
2. 复制波形时完整继承源波形底层参数。
3. 保存自定义波形时，未开放字段沿用原值。

### 内置与自定义规则

内置波形：

1. `builtin = true`
2. 只读查看
3. 不允许删除
4. 不允许直接保存覆盖
5. 允许复制为自定义

自定义波形：

1. `builtin = false`
2. 允许编辑名称
3. 允许拖拽和分段编辑
4. 允许删除

## 存储与仓库设计

### DAO 扩展

当前 [WaveformDao.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/storage/dao/WaveformDao.kt) 仅支持查询和批量写入，需扩展为完整 CRUD：

1. `upsert(waveform: WaveformEntity)`
2. `deleteById(id: String)`
3. 保留现有：
   - `observeAll()`
   - `count()`
   - `insertAll()`
   - `findById(id)`

[JsonWaveformDao.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/storage/JsonWaveformDao.kt) 同步实现上述接口，继续使用 UTF-8 JSON 落盘。

### 新增波形管理仓库

建议新增 `WaveformLibraryRepository`，职责集中处理：

1. 创建空白自定义波形
2. 复制现有波形
3. 保存自定义波形
4. 删除自定义波形
5. 时长和强度归一化
6. 生成唯一 `custom-wave-xxxxxxxx` ID
7. 删除前检查规则引用

不要让 `ViewModel` 直接编排 DAO 和规则引用逻辑，避免页面状态层承担业务规则。

### 默认值与归一化

新建空白波形默认值：

1. `name = "自定义波形"`
2. `builtin = false`
3. 至少包含 1 个分段
4. 默认分段：
   - `durationMs = 200`
   - `channelA = 0`
   - `channelB = 0`

归一化规则：

1. `durationMs >= 1`
2. `channelA` 和 `channelB` 限制在 `0..180`

## 页面状态设计

建议新增 `WaveformsViewModel`，并把“列表态”和“草稿态”彻底拆开。

### 核心状态

1. `waveforms`
   - 当前持久化层全量波形
2. `selectedWaveformId`
   - 当前选中的波形 ID
3. `draftWaveform`
   - 当前草稿副本
4. `isDirty`
   - 当前草稿是否未保存
5. `editorMessage`
   - 当前编辑状态提示
6. `pendingDeleteWaveformId`
   - 删除确认用
7. `dragSession`
   - 拖拽中的瞬时态，不做持久化

### 状态流原则

1. 列表数据来自 `observeAll()`
2. 草稿只在选中某个波形时从列表快照复制一份
3. 编辑时只改草稿，不直接改列表
4. 保存成功后，再由持久化流回灌刷新

## 页面交互设计

### 初始化

1. 首次进入波形库页后订阅全部波形。
2. 若有数据则默认选中第一条。
3. 生成 `draftWaveform = selected.copy()`。

### 切换波形

1. 若当前无脏状态，直接切换。
2. 若有脏状态，先弹确认：
   - `当前波形还有未保存更改，是否放弃修改并切换？`
3. 确认后切换选中项并重建草稿。
4. 取消则停留当前波形。

### 新建波形

1. 点击“新建空白波形”后由仓库创建。
2. 成功后自动选中新波形。
3. 进入可编辑态，`isDirty = false`。

### 复制波形

1. 内置和自定义波形都允许复制。
2. 内置波形进入编辑的唯一入口是“复制为自定义”。
3. 复制后自动切换到新副本。
4. 新副本 `builtin = false`。

### 删除波形

1. 只允许删除自定义波形。
2. 删除前弹确认框。
3. 若被规则引用，则阻止删除并提示：
   - `请先修改规则绑定后再删除该波形`
4. 删除成功后自动选择下一条可用波形，若无则进入空态。

### 内置波形只读态

选中内置波形时：

1. 名称输入框禁用
2. 保存按钮禁用
3. 删除按钮禁用
4. 拖拽手柄禁用
5. 分段明细只读
6. “复制为自定义”作为主操作

## 可视化编辑器设计

### 渲染模型

编辑器使用 Compose `Canvas` 绘制：

1. 横轴按每段 `durationMs` 占据相对宽度
2. 纵轴固定为 `0..180`
3. `channelA` 使用高亮主色
4. `channelB` 使用辅助对比色
5. 每段绘制两个拖拽手柄
6. 每段边界绘制插入热点

### 拖拽编辑

1. 命中 A/B 手柄后，拖拽纵向位置映射到 `0..180`
2. 映射值实时回写 `draftWaveform`
3. 拖拽开始即标记 `isDirty = true`
4. 坐标映射逻辑尽量拆为纯 Kotlin 函数，便于单测

### 新增分段

1. 在段与段的边界位置提供插入点
2. 点击后在对应索引插入新分段
3. 新分段默认继承左侧段参数，以保持连续编辑手感
4. 同时保留一个显式“新增分段”按钮，便于兜底

### 分段精修区

在画布下方保留明细列表，支持：

1. 修改 `durationMs`
2. 修改 `channelA`
3. 修改 `channelB`
4. 复制当前分段
5. 删除当前分段

删除约束：

1. 波形至少保留 1 个分段

## 规则联动设计

1. [RulesViewModel.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesViewModel.kt) 继续从 `waveformDao.observeAll()` 获取波形选项。
2. 波形库的新增、删除、保存结果通过同一数据源自然同步到规则页。
3. 删除波形前检查 [JsonRuleStore.kt](/D:/BiliLive-YOKONEX-App/app/src/main/java/com/yokonex/bililive/data/storage/JsonRuleStore.kt) 中是否仍有规则绑定该波形。

## 测试策略

### 仓库层测试

新增波形管理测试，覆盖：

1. 创建空白自定义波形
2. 复制内置波形为自定义波形
3. 保存自定义波形
4. 删除未被引用的自定义波形
5. 删除被规则引用的自定义波形失败
6. 内置波形直接保存或删除失败

### DAO 与映射测试

1. 为 `JsonWaveformDao` 增加 `upsert / deleteById` 测试
2. 保持 UTF-8 JSON 读写一致性
3. 对强度和时长归一化逻辑做单测

### ViewModel 测试

新增 `WaveformsViewModelTest`，覆盖：

1. 首次加载默认选中第一条波形
2. 编辑后 `isDirty = true`
3. 保存后 `isDirty = false`
4. 复制后自动跳到副本
5. 删除后自动切换下一条
6. 删除失败时暴露明确错误消息

### UI 与几何逻辑测试

1. 轻量 Compose screen test 验证“波形库”页基础渲染
2. 将拖拽命中和坐标映射逻辑提炼为纯 Kotlin 函数，并做 JVM 单测：
   - `strengthFromCanvasY`
   - `segmentIndexFromCanvasX`
   - `insertIndexFromBoundaryX`

## 实现建议

推荐按以下顺序实现：

1. 先扩展 `WaveformDao` 和 `JsonWaveformDao`
2. 新增波形管理仓库与相关测试
3. 新增 `WaveformsViewModel`
4. 新增波形库页面和底部导航入口
5. 接入可视化编辑器与拖拽
6. 最后补齐规则引用保护、提示文案和回归测试

## 风险与取舍

1. 最大技术风险在 Compose 里的拖拽命中和分段插入位置判断。
2. 为降低风险，应把几何计算和坐标映射逻辑从 UI 中抽离为纯 Kotlin 函数。
3. 页面视觉保持当前 App Material 3 语言，不复刻 Web 页面样式细节。

## 成功标准

满足以下条件即视为本次功能达标：

1. 底部导航出现独立“波形库”菜单。
2. 用户可以浏览全部内置和自定义波形。
3. 用户可以新建空白波形。
4. 用户可以复制内置波形为自定义波形。
5. 用户可以通过拖拽修改 A/B 强度。
6. 用户可以通过可视化交互或明细列表新增分段。
7. 自定义波形保存后规则页可立即使用。
8. 已被规则绑定的自定义波形无法被误删。
9. 内置波形始终保持只读。
