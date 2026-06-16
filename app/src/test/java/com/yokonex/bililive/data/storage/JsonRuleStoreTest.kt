package com.yokonex.bililive.data.storage

import com.yokonex.bililive.domain.model.CooldownScope
import com.yokonex.bililive.domain.model.KeywordMatchMode
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.RuleConditions
import com.yokonex.bililive.domain.model.TriggerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRuleStoreTest {

    @Test
    fun init_migratesDanmakuCooldownScopeAndBackfillsMissingDefaultRules() {
        val file = kotlin.io.path.createTempFile("rule-store", ".json").toFile()
        file.writeText(
            """
            [
              {
                "id": "danmaku-default",
                "name": "弹幕默认规则",
                "enabled": false,
                "eventType": "DANMAKU",
                "cooldownSeconds": 0,
                "cooldownScope": "",
                "conditionsJson": "minPrice=;maxPrice=;likeMultiple=;minGuardLevel=0;keywords=开火;matchMode=ANY",
                "actionBindingsJson": "bluetooth=ems-preset-03;websocket=command_three"
              }
            ]
            """.trimIndent(),
        )

        val store = JsonRuleStore(
            file = file,
            defaultRules = listOf(
                TriggerRule(
                    id = "danmaku-default",
                    name = "弹幕默认规则",
                    enabled = false,
                    eventType = LiveEventType.DANMAKU,
                    cooldownScope = CooldownScope.PER_USER,
                    conditions = RuleConditions(
                        keywords = emptyList(),
                        matchMode = KeywordMatchMode.ANY,
                    ),
                ),
                TriggerRule(
                    id = "danmaku-captain-default",
                    name = "舰长弹幕规则",
                    enabled = false,
                    eventType = LiveEventType.DANMAKU_CAPTAIN,
                    cooldownScope = CooldownScope.PER_USER,
                    conditions = RuleConditions(
                        minGuardLevel = 3,
                    ),
                ),
            ),
        )

        val danmakuRule = store.rules.value.firstOrNull { it.id == "danmaku-default" }
        val captainRule = store.rules.value.firstOrNull { it.id == "danmaku-captain-default" }

        assertNotNull(danmakuRule)
        assertNotNull(captainRule)
        assertEquals(CooldownScope.PER_USER, danmakuRule!!.cooldownScope)
        assertTrue(file.readText().contains("\"cooldownScope\": \"PER_USER\""))
    }
}
