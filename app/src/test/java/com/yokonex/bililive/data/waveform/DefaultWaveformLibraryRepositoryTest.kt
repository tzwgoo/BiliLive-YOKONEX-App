package com.yokonex.bililive.data.waveform

import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.storage.DefaultWaveforms
import com.yokonex.bililive.data.storage.JsonRuleStore
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.data.storage.entity.WaveformEntity
import com.yokonex.bililive.domain.model.ActionBindings
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.RuleConditions
import com.yokonex.bililive.domain.model.TriggerRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DefaultWaveformLibraryRepositoryTest {

    @Test
    fun createWaveform_addsEditableCustomWave() = runTest {
        val repository = createRepository()

        val created = repository.createWaveform(name = "自定义波形")

        assertFalse(created.builtin)
        assertTrue(created.id.startsWith("custom-wave-"))
        assertEquals("自定义波形", created.name)
        assertEquals(1, created.steps.size)
        assertEquals(200, created.steps.first().durationMs)
        assertEquals(0, created.steps.first().channelA)
        assertEquals(0, created.steps.first().channelB)
    }

    @Test
    fun duplicateWaveform_clonesSourceStepsIntoEditableWave() = runTest {
        val source = DefaultWaveforms.all.first()
        val repository = createRepository(
            waveforms = listOf(source).map(WaveformMapper::toEntity),
        )

        val duplicated = repository.duplicateWaveform(
            sourceWaveformId = source.id,
            name = "复制版",
        )

        assertFalse(duplicated.builtin)
        assertEquals("复制版", duplicated.name)
        assertNotEquals(source.id, duplicated.id)
        assertEquals(source.steps, duplicated.steps)
    }

    @Test
    fun saveWaveform_normalizesDurationAndStrength() = runTest {
        val created = DefaultWaveforms.all.first().copy(
            id = "custom-wave-01",
            name = "待保存波形",
            builtin = false,
        )
        val repository = createRepository(
            waveforms = listOf(created).map(WaveformMapper::toEntity),
        )

        val saved = repository.saveWaveform(
            created.copy(
                name = "已保存波形",
                steps = listOf(
                    created.steps.first().copy(
                        durationMs = 0,
                        channelA = 999,
                        channelB = -15,
                    ),
                ),
            ),
        )

        assertEquals("已保存波形", saved.name)
        assertEquals(1, saved.steps.first().durationMs)
        assertEquals(180, saved.steps.first().channelA)
        assertEquals(0, saved.steps.first().channelB)
    }

    @Test
    fun deleteWaveform_throwsWhenReferencedByRule() = runTest {
        val custom = DefaultWaveforms.all.first().copy(
            id = "custom-wave-01",
            builtin = false,
            name = "规则绑定波形",
        )
        val ruleStore = JsonRuleStore(
            file = kotlin.io.path.createTempFile("rule-store", ".json").toFile(),
            defaultRules = listOf(
                TriggerRule(
                    id = "rule-01",
                    name = "测试规则",
                    eventType = LiveEventType.LIKE,
                    conditions = RuleConditions(),
                    actionBindings = ActionBindings(
                        bluetoothAction = OutputAction.BluetoothWaveformAction(custom.id),
                    ),
                ),
            ),
        )
        val repository = createRepository(
            waveforms = listOf(custom).map(WaveformMapper::toEntity),
            ruleStore = ruleStore,
        )

        val error = try {
            repository.deleteWaveform(custom.id)
            null
        } catch (exception: IllegalStateException) {
            exception
        }

        if (error == null) {
            fail("expected deleteWaveform to throw IllegalStateException")
        }
        assertEquals("请先修改规则绑定后再删除该波形", error!!.message)
    }

    @Test
    fun deleteWaveform_removesEditableCustomWave() = runTest {
        val custom = DefaultWaveforms.all.first().copy(
            id = "custom-wave-02",
            builtin = false,
            name = "待删除波形",
        )
        val waveformDao = FakeWaveformDao(
            initial = listOf(WaveformMapper.toEntity(custom)),
        )
        val repository = createRepository(
            waveformDao = waveformDao,
        )

        repository.deleteWaveform(custom.id)

        val remaining = waveformDao.observeAll().first()
        assertTrue(remaining.none { it.id == custom.id })
    }

    private fun createRepository(
        waveforms: List<WaveformEntity> = emptyList(),
        waveformDao: FakeWaveformDao = FakeWaveformDao(waveforms),
        ruleStore: JsonRuleStore = JsonRuleStore(
            file = kotlin.io.path.createTempFile("rule-store", ".json").toFile(),
            defaultRules = emptyList(),
        ),
    ): DefaultWaveformLibraryRepository = DefaultWaveformLibraryRepository(
        waveformDao = waveformDao,
        ruleStore = ruleStore,
    )

    private class FakeWaveformDao(
        initial: List<WaveformEntity> = emptyList(),
    ) : WaveformDao {
        private val state = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<WaveformEntity>> = state

        override suspend fun count(): Int = state.value.size

        override suspend fun insertAll(waveforms: List<WaveformEntity>) {
            state.value = waveforms.sortedBy(WaveformEntity::name)
        }

        override suspend fun upsert(waveform: WaveformEntity) {
            state.value = (state.value.filterNot { entity -> entity.id == waveform.id } + waveform)
                .sortedBy(WaveformEntity::name)
        }

        override suspend fun findById(id: String): WaveformEntity? =
            state.value.firstOrNull { it.id == id }

        override suspend fun deleteById(id: String) {
            state.value = state.value.filterNot { entity -> entity.id == id }
        }
    }
}
