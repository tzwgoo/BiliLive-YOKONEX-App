package com.yokonex.bililive.data.mapper

import com.yokonex.bililive.domain.model.ActionBindings
import com.yokonex.bililive.domain.model.CooldownScope
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.RuleConditions
import com.yokonex.bililive.domain.model.TriggerRule
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleMapperTest {

    @Test
    fun roundTrip_preservesUserLimitAndGuardWaveformOverrides() {
        val rule = TriggerRule(
            id = "gift-rule",
            name = "礼物规则",
            eventType = LiveEventType.GIFT,
            cooldownScope = CooldownScope.PER_USER,
            conditions = RuleConditions(
                minPrice = 100,
                minGuardLevel = 3,
                userLimitWindowSeconds = 30,
                userLimitMaxTriggers = 2,
            ),
            actionBindings = ActionBindings(
                bluetoothAction = OutputAction.BluetoothWaveformAction("ems-preset-06"),
                websocketAction = OutputAction.WebSocketCommandAction("command_two"),
                guardWaveformIds = mapOf(
                    0 to "ems-preset-01",
                    3 to "ems-preset-04",
                    1 to "ems-preset-09",
                ),
            ),
        )

        val entity = RuleMapper.toEntity(rule)
        val restored = RuleMapper.fromEntity(entity)

        assertEquals(30, restored.conditions.userLimitWindowSeconds)
        assertEquals(2, restored.conditions.userLimitMaxTriggers)
        assertEquals("ems-preset-01", restored.actionBindings.guardWaveformIds[0])
        assertEquals("ems-preset-04", restored.actionBindings.guardWaveformIds[3])
        assertEquals("ems-preset-09", restored.actionBindings.guardWaveformIds[1])
    }
}
