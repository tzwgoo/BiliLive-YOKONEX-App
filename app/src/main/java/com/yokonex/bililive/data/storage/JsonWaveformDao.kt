package com.yokonex.bililive.data.storage

import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.data.storage.entity.WaveformEntity
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JsonWaveformDao(
    private val file: File,
    defaultWaveforms: List<WaveformEntity>,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) : WaveformDao {
    private val state = MutableStateFlow(loadInitial(defaultWaveforms))

    override fun observeAll(): Flow<List<WaveformEntity>> = state.asStateFlow()

    override suspend fun count(): Int = state.value.size

    override suspend fun insertAll(waveforms: List<WaveformEntity>) {
        state.value = waveforms.sortedBy(WaveformEntity::name)
        persist()
    }

    override suspend fun upsert(waveform: WaveformEntity) {
        val filtered = state.value.filterNot { entity -> entity.id == waveform.id }
        state.value = (filtered + waveform).sortedBy(WaveformEntity::name)
        persist()
    }

    override suspend fun findById(id: String): WaveformEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { entity -> entity.id == id }
        persist()
    }

    private fun loadInitial(defaultWaveforms: List<WaveformEntity>): List<WaveformEntity> {
        if (!file.exists()) {
            persist(defaultWaveforms)
            return defaultWaveforms.sortedBy(WaveformEntity::name)
        }
        val content = file.readText(Charsets.UTF_8)
        if (content.isBlank()) {
            persist(defaultWaveforms)
            return defaultWaveforms.sortedBy(WaveformEntity::name)
        }
        return runCatching {
            json.parseToJsonElement(content).jsonArray.map(::jsonToWaveformEntity)
        }.getOrElse {
            persist(defaultWaveforms)
            defaultWaveforms
        }.sortedBy(WaveformEntity::name)
    }

    private fun persist(source: List<WaveformEntity> = state.value) {
        file.parentFile?.mkdirs()
        val payload = buildJsonArray {
            source.forEach { entity ->
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(entity.id))
                        put("name", JsonPrimitive(entity.name))
                        put("builtin", JsonPrimitive(entity.builtin))
                        put("payloadJson", JsonPrimitive(entity.payloadJson))
                    },
                )
            }
        }
        file.writeText(json.encodeToString(JsonArray.serializer(), payload), Charsets.UTF_8)
    }

    private fun jsonToWaveformEntity(element: kotlinx.serialization.json.JsonElement): WaveformEntity {
        val obj = element.jsonObject
        return WaveformEntity(
            id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
            name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
            builtin = obj["builtin"]?.jsonPrimitive?.booleanOrNull ?: false,
            payloadJson = obj["payloadJson"]?.jsonPrimitive?.content.orEmpty(),
        )
    }
}
