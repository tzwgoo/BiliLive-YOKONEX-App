# 波形库与可视化编辑器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `D:\BiliLive-YOKONEX-App` 中新增独立“波形库”底部菜单，并实现波形的可视化管理、拖拽编辑、分段新增/复制/删除与规则引用保护。

**Architecture:** 复用现有 `WaveformDefinition + JsonWaveformDao + RulesViewModel` 链路，在 data 层新增波形管理仓库承接 CRUD、校验和规则引用保护，在 UI 层新增 `WaveformsViewModel + WaveformsScreen + Canvas 几何工具`，通过“列表快照 + 草稿副本”模式隔离未保存编辑状态。拖拽命中和坐标映射逻辑拆成纯 Kotlin 函数，Compose 只负责渲染与事件分发。

**Tech Stack:** Kotlin、Jetpack Compose、Material 3、Navigation Compose、ViewModel、Coroutines、Flow、kotlinx.serialization、JUnit4、Compose UI Test

---

## 前置说明

1. 当前工作区存在用户已有未提交改动，执行本计划时不得回滚无关文件。
2. 波形库实现以 [2026-05-28-waveform-library-design.md](/D:/BiliLive-YOKONEX-App/docs/superpowers/specs/2026-05-28-waveform-library-design.md) 为准。
3. 本计划默认继续沿用 UTF-8 JSON 存储，不引入 Room 迁移。
4. 内置波形始终只读，自定义波形才可保存和删除。

## 文件结构与职责

**存储与仓库**
- Modify: `app/src/main/java/com/yokonex/bililive/data/storage/dao/WaveformDao.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/data/storage/JsonWaveformDao.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/waveform/WaveformLibraryRepository.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/waveform/DefaultWaveformLibraryRepository.kt`

**应用容器与规则联动**
- Modify: `app/src/main/java/com/yokonex/bililive/AppContainer.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesViewModel.kt`

**波形库 UI**
- Modify: `app/src/main/java/com/yokonex/bililive/app/navigation/AppNavGraph.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformsViewModel.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformsScreen.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformEditorCanvas.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformCanvasGeometry.kt`

**测试**
- Create: `app/src/test/java/com/yokonex/bililive/data/waveform/DefaultWaveformLibraryRepositoryTest.kt`
- Modify: `app/src/test/java/com/yokonex/bililive/data/storage/SettingsStoreTest.kt`
- Create: `app/src/test/java/com/yokonex/bililive/app/ui/waveforms/WaveformsViewModelTest.kt`
- Create: `app/src/test/java/com/yokonex/bililive/app/ui/waveforms/WaveformCanvasGeometryTest.kt`
- Modify: `app/src/test/java/com/yokonex/bililive/app/ui/rules/RulesViewModelTest.kt`
- Create: `app/src/androidTest/java/com/yokonex/bililive/app/ui/waveforms/WaveformsScreenTest.kt`

## 实施原则

1. 先补仓库与几何工具的 JVM 测试，再接 UI。
2. 每个任务先写失败测试，再写最小实现，再跑验证。
3. 拖拽逻辑不直接写死在 `pointerInput` 中，优先提炼成纯函数。
4. UI 只维护草稿态，不直接持久化。
5. 每个任务结束后都做小范围验证与一次提交。

### Task 1: 扩展波形 DAO 并建立波形管理仓库

**Files:**
- Modify: `app/src/main/java/com/yokonex/bililive/data/storage/dao/WaveformDao.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/data/storage/JsonWaveformDao.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/waveform/WaveformLibraryRepository.kt`
- Create: `app/src/main/java/com/yokonex/bililive/data/waveform/DefaultWaveformLibraryRepository.kt`
- Create: `app/src/test/java/com/yokonex/bililive/data/waveform/DefaultWaveformLibraryRepositoryTest.kt`

- [ ] **Step 1: 为创建、复制、保存、删除波形写失败测试**

```kotlin
@Test
fun createWaveform_addsEditableCustomWave() = runTest {
    val created = repository.createWaveform(name = "自定义波形")

    assertEquals(false, created.builtin)
    assertTrue(created.id.startsWith("custom-wave-"))
    assertEquals(1, created.steps.size)
}
```

- [ ] **Step 2: 为删除被规则引用的自定义波形写失败测试**

```kotlin
@Test
fun deleteWaveform_throwsWhenReferencedByRule() = runTest {
    val error = assertFailsWith<IllegalStateException> {
        repository.deleteWaveform("custom-wave-01")
    }

    assertEquals("请先修改规则绑定后再删除该波形", error.message)
}
```

- [ ] **Step 3: 运行仓库测试，确认当前失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "*DefaultWaveformLibraryRepositoryTest"`
Expected: FAIL，提示仓库接口或 DAO 方法未实现

- [ ] **Step 4: 扩展 `WaveformDao` 接口，加入 `upsert` 和 `deleteById`**

```kotlin
suspend fun upsert(waveform: WaveformEntity)
suspend fun deleteById(id: String)
```

- [ ] **Step 5: 在 `JsonWaveformDao` 中实现增删改持久化**

```kotlin
override suspend fun upsert(waveform: WaveformEntity) {
    val filtered = state.value.filterNot { it.id == waveform.id }
    state.value = (filtered + waveform).sortedBy(WaveformEntity::name)
    persist()
}
```

- [ ] **Step 6: 实现 `WaveformLibraryRepository` 与默认实现**

```kotlin
interface WaveformLibraryRepository {
    suspend fun createWaveform(name: String = "自定义波形"): WaveformDefinition
    suspend fun duplicateWaveform(sourceWaveformId: String, name: String? = null): WaveformDefinition
    suspend fun saveWaveform(waveform: WaveformDefinition): WaveformDefinition
    suspend fun deleteWaveform(waveformId: String)
}
```

- [ ] **Step 7: 在仓库中实现规则引用检查、唯一 ID 生成和强度归一化**

```kotlin
private fun normalizeStrength(value: Int): Int = value.coerceIn(0, 180)
private fun normalizeDuration(value: Int): Int = value.coerceAtLeast(1)
```

- [ ] **Step 8: 重新运行仓库测试，确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "*DefaultWaveformLibraryRepositoryTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/data/storage/dao/WaveformDao.kt app/src/main/java/com/yokonex/bililive/data/storage/JsonWaveformDao.kt app/src/main/java/com/yokonex/bililive/data/waveform app/src/test/java/com/yokonex/bililive/data/waveform/DefaultWaveformLibraryRepositoryTest.kt
git commit -m "feat(waveform): 建立波形管理仓库与持久化 CRUD" -m "扩展 WaveformDao 和 JsonWaveformDao，新增波形仓库统一处理创建、复制、保存、删除、规则引用保护与参数归一化，为波形库页面提供稳定数据入口。"
```

### Task 2: 提炼画布几何逻辑并补齐规则联动测试

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformCanvasGeometry.kt`
- Create: `app/src/test/java/com/yokonex/bililive/app/ui/waveforms/WaveformCanvasGeometryTest.kt`
- Modify: `app/src/test/java/com/yokonex/bililive/app/ui/rules/RulesViewModelTest.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesViewModel.kt`

- [ ] **Step 1: 为拖拽纵坐标映射到强度写失败测试**

```kotlin
@Test
fun strengthFromCanvasY_mapsTopTo180AndBottomTo0() {
    assertEquals(180, strengthFromCanvasY(y = 0f, height = 240f))
    assertEquals(0, strengthFromCanvasY(y = 240f, height = 240f))
}
```

- [ ] **Step 2: 为边界插入索引计算写失败测试**

```kotlin
@Test
fun insertIndexFromBoundaryX_returnsMiddleBoundaryIndex() {
    val index = insertIndexFromBoundaryX(
        x = 100f,
        segmentWidths = listOf(80f, 40f, 120f),
        tolerance = 24f,
    )

    assertEquals(1, index)
}
```

- [ ] **Step 3: 为规则页继续暴露波形选项写失败测试**

```kotlin
@Test
fun waveformOptions_followDaoUpdates() = runTest {
    fakeWaveformDao.emit(listOf(customWaveformEntity))

    val firstRule = viewModel.uiState.value.rules.first()
    assertTrue(firstRule.waveformOptions.any { it.id == "custom-wave-01" })
}
```

- [ ] **Step 4: 运行相关单测，确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "*WaveformCanvasGeometryTest" --tests "*RulesViewModelTest"`
Expected: FAIL，提示几何工具未实现或规则测试前置不满足

- [ ] **Step 5: 实现 `WaveformCanvasGeometry.kt`，提供纯函数工具**

```kotlin
fun strengthFromCanvasY(y: Float, height: Float): Int
fun segmentIndexFromCanvasX(x: Float, segmentWidths: List<Float>): Int?
fun insertIndexFromBoundaryX(x: Float, segmentWidths: List<Float>, tolerance: Float): Int?
```

- [ ] **Step 6: 调整 `RulesViewModel` 构造与测试桩，使波形流更新可观察**

```kotlin
private var currentWaveforms: List<UiWaveformOption> = sampleWaveformOptions()
```

- [ ] **Step 7: 重新运行单测，确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "*WaveformCanvasGeometryTest" --tests "*RulesViewModelTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformCanvasGeometry.kt app/src/test/java/com/yokonex/bililive/app/ui/waveforms/WaveformCanvasGeometryTest.kt app/src/main/java/com/yokonex/bililive/app/ui/rules/RulesViewModel.kt app/src/test/java/com/yokonex/bililive/app/ui/rules/RulesViewModelTest.kt
git commit -m "test(waveform): 固化画布几何与规则联动基础" -m "提炼 Canvas 坐标映射纯函数，并补齐规则页对波形列表更新的测试，降低后续拖拽交互实现风险。"
```

### Task 3: 实现波形库 ViewModel 与草稿状态流

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformsViewModel.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/AppContainer.kt`
- Create: `app/src/test/java/com/yokonex/bililive/app/ui/waveforms/WaveformsViewModelTest.kt`

- [ ] **Step 1: 为默认选中第一条波形写失败测试**

```kotlin
@Test
fun init_selectsFirstWaveformAndBuildsDraft() = runTest {
    val state = viewModel.uiState.value

    assertEquals("ems-preset-01", state.selectedWaveformId)
    assertEquals("ems-preset-01", state.draftWaveform?.id)
}
```

- [ ] **Step 2: 为脏草稿切换确认、复制后跳转和保存后清脏写失败测试**

```kotlin
@Test
fun saveDraft_clearsDirtyFlag() = runTest {
    viewModel.updateWaveformName("新的名字")
    viewModel.saveDraft()

    assertEquals(false, viewModel.uiState.value.isDirty)
}
```

- [ ] **Step 3: 运行 ViewModel 单测，确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "*WaveformsViewModelTest"`
Expected: FAIL，提示 ViewModel 尚未实现

- [ ] **Step 4: 在 `AppContainer` 中注册 `waveformLibraryRepository`**

```kotlin
val waveformLibraryRepository: WaveformLibraryRepository = DefaultWaveformLibraryRepository(
    waveformDao = waveformDao,
    ruleStore = ruleStore,
)
```

- [ ] **Step 5: 创建 `WaveformsUiState` 与 `WaveformsViewModel`**

```kotlin
data class WaveformsUiState(
    val waveforms: List<UiWaveformCard> = emptyList(),
    val selectedWaveformId: String = "",
    val draftWaveform: WaveformDefinition? = null,
    val isDirty: Boolean = false,
    val editorMessage: String = "",
)
```

- [ ] **Step 6: 实现草稿编辑、保存、复制、新建、删除与确认框状态**

```kotlin
fun selectWaveform(waveformId: String)
fun createWaveform()
fun duplicateSelectedWaveform()
fun saveDraft()
fun requestDeleteSelectedWaveform()
fun confirmDeleteSelectedWaveform()
```

- [ ] **Step 7: 重新运行 ViewModel 单测，确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "*WaveformsViewModelTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/AppContainer.kt app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformsViewModel.kt app/src/test/java/com/yokonex/bililive/app/ui/waveforms/WaveformsViewModelTest.kt
git commit -m "feat(waveform): 建立波形库页面状态流" -m "新增 WaveformsViewModel 和草稿态管理，打通列表快照、选中态、未保存修改、创建复制保存删除等页面行为。"
```

### Task 4: 新增波形库页面与底部导航入口

**Files:**
- Modify: `app/src/main/java/com/yokonex/bililive/app/navigation/AppNavGraph.kt`
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformsScreen.kt`
- Create: `app/src/androidTest/java/com/yokonex/bililive/app/ui/waveforms/WaveformsScreenTest.kt`

- [ ] **Step 1: 为底部导航出现“波形库”入口写失败测试**

```kotlin
@Test
fun waveformTab_isVisibleInBottomNavigation() {
    composeTestRule.onNodeWithText("波形库").assertExists()
}
```

- [ ] **Step 2: 为波形库页基础渲染写失败测试**

```kotlin
@Test
fun waveformScreen_showsLibraryAndEditorSections() {
    composeTestRule.onNodeWithText("波形库").assertExists()
    composeTestRule.onNodeWithText("保存波形").assertExists()
}
```

- [ ] **Step 3: 运行仪表测试，确认当前失败**

Run: `.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yokonex.bililive.app.ui.waveforms.WaveformsScreenTest`
Expected: FAIL，提示页面或导航入口尚未存在

- [ ] **Step 4: 在 `AppNavGraph` 中加入第 6 个底部导航项和 `waveforms` route**

```kotlin
NavigationItem("waveforms", "波形库", "形")
```

- [ ] **Step 5: 创建 `WaveformsScreen` 的列表区和编辑区骨架**

```kotlin
LazyColumn {
    item { Text("波形库") }
    item { WaveformLibrarySection(...) }
    item { WaveformEditorSection(...) }
}
```

- [ ] **Step 6: 接入 `WaveformsViewModel`，渲染卡片、名称输入、按钮和状态文案**

```kotlin
Button(onClick = onCreateWaveform) { Text("新建空白波形") }
OutlinedButton(onClick = onDuplicate) { Text("复制为自定义") }
```

- [ ] **Step 7: 再次运行仪表测试，确认通过**

Run: `.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yokonex.bililive.app.ui.waveforms.WaveformsScreenTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/app/navigation/AppNavGraph.kt app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformsScreen.kt app/src/androidTest/java/com/yokonex/bililive/app/ui/waveforms/WaveformsScreenTest.kt
git commit -m "feat(ui): 新增独立波形库菜单与页面骨架" -m "在底部导航加入波形库入口，并落地波形列表、编辑操作区和基础状态展示，为后续拖拽编辑器接入提供页面承载。"
```

### Task 5: 接入可视化编辑器、分段操作与最终回归

**Files:**
- Create: `app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformEditorCanvas.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformsScreen.kt`
- Modify: `app/src/main/java/com/yokonex/bililive/app/ui/waveforms/WaveformsViewModel.kt`
- Modify: `app/src/test/java/com/yokonex/bililive/app/ui/waveforms/WaveformsViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/yokonex/bililive/app/ui/waveforms/WaveformsScreenTest.kt`

- [ ] **Step 1: 为拖拽更新 A/B 强度写失败测试**

```kotlin
@Test
fun updateDraftStrength_marksDirtyAndChangesChannel() = runTest {
    viewModel.updateDraftStrength(stepIndex = 0, channel = WaveformChannel.A, strength = 88)

    assertEquals(88, viewModel.uiState.value.draftWaveform?.steps?.first()?.channelA)
    assertEquals(true, viewModel.uiState.value.isDirty)
}
```

- [ ] **Step 2: 为插入分段、复制分段、删除最后一段保护写失败测试**

```kotlin
@Test
fun insertStepAfterBoundary_addsOneStep() = runTest {
    viewModel.insertStep(insertIndex = 1)

    assertEquals(2, viewModel.uiState.value.draftWaveform?.steps?.size)
}
```

- [ ] **Step 3: 运行 ViewModel 与 UI 测试，确认失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "*WaveformsViewModelTest"`
Expected: FAIL，提示分段编辑行为未实现

- [ ] **Step 4: 创建 `WaveformEditorCanvas`，实现 Canvas 渲染和 `pointerInput` 事件转发**

```kotlin
Canvas(modifier = modifier.pointerInput(uiState.editorKey) {
    detectDragGestures(
        onDragStart = { offset -> ... },
        onDrag = { change, _ -> ... },
    )
})
```

- [ ] **Step 5: 在 `WaveformsViewModel` 中补齐分段编辑方法**

```kotlin
fun updateDraftStrength(stepIndex: Int, channel: WaveformChannel, strength: Int)
fun insertStep(insertIndex: Int)
fun duplicateStep(stepIndex: Int)
fun deleteStep(stepIndex: Int)
fun updateStepDuration(stepIndex: Int, durationMs: Int)
```

- [ ] **Step 6: 在 `WaveformsScreen` 中接入拖拽编辑器和分段精修列表**

```kotlin
WaveformEditorCanvas(
    waveform = uiState.draftWaveform,
    editable = uiState.isSelectedWaveformEditable,
    onStrengthDrag = viewModel::updateDraftStrength,
    onInsertStep = viewModel::insertStep,
)
```

- [ ] **Step 7: 运行波形相关单测与仪表测试**

Run: `.\gradlew.bat testDebugUnitTest --tests "*DefaultWaveformLibraryRepositoryTest" --tests "*WaveformCanvasGeometryTest" --tests "*WaveformsViewModelTest" --tests "*RulesViewModelTest"`
Expected: PASS

Run: `.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yokonex.bililive.app.ui.waveforms.WaveformsScreenTest`
Expected: PASS

- [ ] **Step 8: 运行更大范围回归**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 9: 手动验证关键流程**

Run: `.\gradlew.bat installDebug`
Expected: App 成功安装到测试机

Manual checklist:
- 打开底部“波形库”菜单
- 查看内置波形只读状态
- 复制内置波形为自定义
- 拖拽 A/B 手柄后保存
- 通过边界插入新分段
- 删除被规则引用的自定义波形时看到阻止提示
- 返回规则页确认下拉中出现最新自定义波形

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/yokonex/bililive/app/ui/waveforms app/src/test/java/com/yokonex/bililive/app/ui/waveforms app/src/androidTest/java/com/yokonex/bililive/app/ui/waveforms app/src/main/java/com/yokonex/bililive/app/navigation/AppNavGraph.kt
git commit -m "feat(waveform): 完成波形库与可视化编辑主流程" -m "接入 Canvas 拖拽编辑器、分段增删复制、波形库列表与底部导航入口，并打通规则联动和删除保护，使安卓端具备完整的波形管理与编辑能力。"
```
