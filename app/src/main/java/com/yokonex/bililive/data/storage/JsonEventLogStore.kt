package com.yokonex.bililive.data.storage

import com.yokonex.bililive.data.storage.entity.EventLogEntity
import com.yokonex.bililive.domain.usecase.EventLogRepository
import com.yokonex.bililive.domain.usecase.ProcessedEventRecord
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JsonEventLogStore(
    private val file: File,
    private val maxEntries: Int = 200,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) : EventLogRepository {
    private val entityState = MutableStateFlow(loadInitial())
    val logs: StateFlow<List<EventLogEntity>> = entityState.asStateFlow()

    override suspend fun record(record: ProcessedEventRecord) {
        entityState.value = (listOf(record.toEntity()) + entityState.value)
            .distinctBy(EventLogEntity::id)
            .sortedByDescending(EventLogEntity::createdAt)
            .take(maxEntries)
        persist()
    }

    private fun loadInitial(): List<EventLogEntity> {
        if (!file.exists()) {
            persist(emptyList())
            return emptyList()
        }
        val content = file.readText(Charsets.UTF_8)
        if (content.isBlank()) {
            return emptyList()
        }
        return runCatching {
            json.parseToJsonElement(content).jsonArray.map(::jsonToEventLogEntity)
        }.getOrDefault(emptyList()).sortedByDescending(EventLogEntity::createdAt)
    }

    private fun persist(source: List<EventLogEntity> = entityState.value) {
        file.parentFile?.mkdirs()
        val payload = buildJsonArray {
            source.forEach { entity ->
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(entity.id))
                        put("eventType", JsonPrimitive(entity.eventType))
                        put("summary", JsonPrimitive(entity.summary))
                        put("rawPayloadJson", JsonPrimitive(entity.rawPayloadJson))
                        put("matchedRuleId", JsonPrimitive(entity.matchedRuleId ?: ""))
                        put("outputMode", JsonPrimitive(entity.outputMode ?: ""))
                        put("outputSuccess", JsonPrimitive(entity.outputSuccess))
                        put("outputMessage", JsonPrimitive(entity.outputMessage ?: ""))
                        put("createdAt", JsonPrimitive(entity.createdAt))
                    },
                )
            }
        }
        file.writeText(json.encodeToString(JsonArray.serializer(), payload), Charsets.UTF_8)
    }

    private fun jsonToEventLogEntity(element: kotlinx.serialization.json.JsonElement): EventLogEntity {
        val obj = element.jsonObject
        return EventLogEntity(
            id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
            eventType = obj["eventType"]?.jsonPrimitive?.content.orEmpty(),
            summary = obj["summary"]?.jsonPrimitive?.content.orEmpty(),
            rawPayloadJson = obj["rawPayloadJson"]?.jsonPrimitive?.content.orEmpty(),
            matchedRuleId = obj["matchedRuleId"]?.jsonPrimitive?.content?.ifBlank { null },
            outputMode = obj["outputMode"]?.jsonPrimitive?.content?.ifBlank { null },
            outputSuccess = obj["outputSuccess"]?.jsonPrimitive?.booleanOrNull ?: false,
            outputMessage = obj["outputMessage"]?.jsonPrimitive?.content?.ifBlank { null },
            createdAt = obj["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        )
    }
}

private fun ProcessedEventRecord.toEntity(): EventLogEntity =
    EventLogEntity(
        id = eventId,
        eventType = eventType,
        summary = summary,
        rawPayloadJson = rawPayloadJson,
        matchedRuleId = matchedRuleId,
        outputMode = outputMode.name,
        outputSuccess = outputSuccess,
        outputMessage = outputMessage,
        createdAt = createdAt,
    )
