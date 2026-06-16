package com.yokonex.bililive.domain.rule

import com.yokonex.bililive.domain.model.ActionBindings
import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.KeywordMatchMode
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.domain.model.RuleConditions
import com.yokonex.bililive.domain.model.TriggerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    @Test
    fun giftRule_matchesWithinPriceRange() {
        val rule = TriggerRule(
            id = "gift-range",
            name = "礼物区间",
            eventType = LiveEventType.GIFT,
            conditions = RuleConditions(
                minPrice = 100,
                maxPrice = 500,
            ),
        )
        val event = LiveEvent(
            id = "event-gift",
            type = LiveEventType.GIFT,
            timestamp = 1L,
            userId = "1001",
            userName = "tester",
            roomId = "2001",
            payload = EventPayload.GiftPayload(
                giftName = "小电视",
                giftNum = 1,
                price = 200,
                totalPrice = 200,
            ),
        )

        val matched = RuleMatcher.matches(rule, event)

        assertTrue(matched)
    }

    @Test
    fun baseGiftRule_matchesSuperChatSubtypeForBackwardCompatibility() {
        val rule = TriggerRule(
            id = "gift-range",
            name = "礼物区间",
            eventType = LiveEventType.GIFT,
            conditions = RuleConditions(
                minPrice = 30,
                maxPrice = 200,
            ),
        )
        val event = LiveEvent(
            id = "event-super-chat",
            type = LiveEventType.SUPER_CHAT,
            timestamp = 1L,
            userId = "1001",
            userName = "tester",
            roomId = "2001",
            payload = EventPayload.GiftPayload(
                giftName = "醒目留言",
                giftNum = 1,
                price = 100,
                totalPrice = 100,
                message = "测试 SC",
            ),
        )

        val matched = RuleMatcher.matches(rule, event)

        assertTrue(matched)
    }

    @Test
    fun likeRule_matchesWhenMultipleReached() {
        val rule = TriggerRule(
            id = "like-multiple",
            name = "点赞倍数",
            eventType = LiveEventType.LIKE,
            conditions = RuleConditions(likeMultiple = 10),
        )
        val event = LiveEvent(
            id = "event-like",
            type = LiveEventType.LIKE,
            timestamp = 2L,
            userId = "1002",
            userName = "tester",
            roomId = "2001",
            payload = EventPayload.LikePayload(
                likeCount = 20,
                likeText = "点赞了",
            ),
        )

        val matched = RuleMatcher.matches(rule, event)

        assertTrue(matched)
    }

    @Test
    fun danmakuRule_matchesWhenKeywordContained() {
        val rule = TriggerRule(
            id = "danmaku-keyword",
            name = "弹幕关键词",
            eventType = LiveEventType.DANMAKU,
            conditions = RuleConditions(
                keywords = listOf("开始", "冲"),
                matchMode = KeywordMatchMode.ANY,
            ),
        )
        val event = LiveEvent(
            id = "event-danmaku",
            type = LiveEventType.DANMAKU,
            timestamp = 3L,
            userId = "1003",
            userName = "tester",
            roomId = "2001",
            payload = EventPayload.DanmakuPayload(message = "大家开始冲"),
        )

        val matched = RuleMatcher.matches(rule, event)

        assertTrue(matched)
    }

    @Test
    fun danmakuRule_rejectsWhenGuardLevelBelowRequirement() {
        val rule = TriggerRule(
            id = "danmaku-guard",
            name = "舰队弹幕",
            eventType = LiveEventType.DANMAKU,
            conditions = RuleConditions(
                minGuardLevel = 2,
                keywords = listOf("开火"),
            ),
        )
        val event = LiveEvent(
            id = "event-danmaku-guard",
            type = LiveEventType.DANMAKU_CAPTAIN,
            timestamp = 3L,
            userId = "1003",
            userName = "tester",
            roomId = "2001",
            payload = EventPayload.DanmakuPayload(
                message = "开火",
                guardLevel = 3,
                guardLabel = "舰长",
            ),
        )

        val matched = RuleMatcher.matches(rule, event)

        assertEquals(false, matched)
    }

    @Test
    fun rule_resolvesBluetoothAction_whenOutputModeIsBluetooth() {
        val action = OutputAction.BluetoothWaveformAction(waveformId = "soft-pulse")
        val rule = TriggerRule(
            id = "action-binding",
            name = "动作绑定",
            eventType = LiveEventType.GIFT,
            actionBindings = ActionBindings(bluetoothAction = action),
        )

        val resolved = RuleMatcher.resolveAction(rule, OutputMode.BLUETOOTH)

        assertEquals(action, resolved)
    }
}
