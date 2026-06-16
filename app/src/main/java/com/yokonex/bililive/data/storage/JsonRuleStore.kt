package com.yokonex.bililive.data.storage

import com.yokonex.bililive.data.mapper.RuleMapper
import com.yokonex.bililive.data.storage.entity.RuleEntity
import com.yokonex.bililive.domain.model.TriggerRule
import com.yokonex.bililive.domain.usecase.RuleRepository
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

class JsonRuleStore(
    private val file: File,
    defaultRules: List<TriggerRule>,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) : RuleRepository {
    private val defaultRuleEntities = defaultRules.map(RuleMapper::toEntity)
    private val entityState = MutableStateFlow(loadInitial(defaultRuleEntities))
    private val ruleState = MutableStateFlow(entityState.value.map(RuleMapper::fromEntity))
    val rules: StateFlow<List<TriggerRule>> = ruleState.asStateFlow()

    override suspend fun getEnabledRules(): List<TriggerRule> =
        rules.value.filter(TriggerRule::enabled)

    suspend fun toggleRule(ruleId: String) {
        entityState.value = entityState.value.map { entity ->
            if (entity.id == ruleId) {
                entity.copy(enabled = !entity.enabled)
            } else {
                entity
            }
        }
        persist()
        syncRules()
    }

    suspend fun updateRule(rule: TriggerRule) {
        val nextEntities = entityState.value.map { entity ->
            if (entity.id == rule.id) {
                RuleMapper.toEntity(rule)
            } else {
                entity
            }
        }
        entityState.value = if (nextEntities.any { entity -> entity.id == rule.id }) {
            nextEntities
        } else {
            nextEntities + RuleMapper.toEntity(rule)
        }.sortedBy(RuleEntity::name)
        persist()
        syncRules()
    }

    private fun loadInitial(defaultRules: List<RuleEntity>): List<RuleEntity> {
        if (!file.exists()) {
            persist(defaultRules)
            return defaultRules.sortedBy(RuleEntity::name)
        }
        val content = file.readText(Charsets.UTF_8)
        if (content.isBlank()) {
            persist(defaultRules)
            return defaultRules.sortedBy(RuleEntity::name)
        }
        return runCatching {
            json.parseToJsonElement(content).jsonArray.map(::jsonToRuleEntity)
        }.getOrElse {
            persist(defaultRules)
            defaultRules
        }.let { entities ->
            normalizeLegacyDefaults(
                entities = entities,
                defaultRules = defaultRules,
            )
        }
            .sortedBy(RuleEntity::name)
    }

    private fun persist(source: List<RuleEntity> = entityState.value) {
        file.parentFile?.mkdirs()
        val payload = buildJsonArray {
            source.forEach { entity ->
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(entity.id))
                        put("name", JsonPrimitive(entity.name))
                        put("enabled", JsonPrimitive(entity.enabled))
                        put("eventType", JsonPrimitive(entity.eventType))
                        put("cooldownSeconds", JsonPrimitive(entity.cooldownSeconds))
                        put("cooldownScope", JsonPrimitive(entity.cooldownScope ?: ""))
                        put("conditionsJson", JsonPrimitive(entity.conditionsJson))
                        put("actionBindingsJson", JsonPrimitive(entity.actionBindingsJson))
                    },
                )
            }
        }
        file.writeText(json.encodeToString(JsonArray.serializer(), payload), Charsets.UTF_8)
    }

    private fun syncRules() {
        ruleState.value = entityState.value.map(RuleMapper::fromEntity)
    }

    private fun jsonToRuleEntity(element: kotlinx.serialization.json.JsonElement): RuleEntity {
        val obj = element.jsonObject
        return RuleEntity(
            id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
            name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
            enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            eventType = obj["eventType"]?.jsonPrimitive?.content.orEmpty(),
            cooldownSeconds = obj["cooldownSeconds"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            cooldownScope = obj["cooldownScope"]?.jsonPrimitive?.content?.ifBlank { null },
            conditionsJson = obj["conditionsJson"]?.jsonPrimitive?.content.orEmpty(),
            actionBindingsJson = obj["actionBindingsJson"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    private fun normalizeLegacyDefaults(
        entities: List<RuleEntity>,
        defaultRules: List<RuleEntity>,
    ): List<RuleEntity> {
        val normalized = entities.map { entity ->
            val rule = RuleMapper.fromEntity(entity)
            when {
                rule.id == "like-default" && rule.conditions.likeMultiple == null -> {
                    RuleMapper.toEntity(
                        rule.copy(
                            conditions = rule.conditions.copy(likeMultiple = 100),
                        ),
                    )
                }

                rule.id == "danmaku-default" &&
                    rule.enabled &&
                    rule.cooldownSeconds == 3 &&
                    rule.conditions.keywords.isEmpty() -> {
                    // 老版本把弹幕规则默认打开且附带冷却，这里回收为新的安全默认值。
                    RuleMapper.toEntity(
                        rule.copy(
                            enabled = false,
                            cooldownSeconds = 0,
                        ),
                    )
                }

                rule.id.startsWith("danmaku-") && rule.cooldownScope == com.yokonex.bililive.domain.model.CooldownScope.GLOBAL -> {
                    // 第二阶段开始弹幕类规则统一支持按用户冷却，老数据在这里补齐迁移。
                    RuleMapper.toEntity(
                        rule.copy(
                            cooldownScope = com.yokonex.bililive.domain.model.CooldownScope.PER_USER,
                        ),
                    )
                }

                else -> entity
            }
        }
        val merged = mergeMissingDefaultRules(
            existingRules = normalized,
            defaultRules = defaultRules,
        )
        if (merged != entities) {
            persist(merged)
        }
        return merged
    }

    private fun mergeMissingDefaultRules(
        existingRules: List<RuleEntity>,
        defaultRules: List<RuleEntity>,
    ): List<RuleEntity> {
        // 旧用户本地规则文件里不会自动出现新事件类型，这里按 ID 补齐缺失的默认规则。
        val existingIds = existingRules.map(RuleEntity::id).toSet()
        val missingRules = defaultRules.filterNot { rule -> rule.id in existingIds }
        if (missingRules.isEmpty()) {
            return existingRules
        }
        return existingRules + missingRules
    }
}
