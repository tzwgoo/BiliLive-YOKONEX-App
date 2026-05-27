package com.yokonex.bililive.app.ui.dashboard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokonex.bililive.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardRoute_isShownOnLaunch() {
        composeTestRule.onNodeWithText("直播控制台").assertExists()
        composeTestRule.onNodeWithText("启动监听").assertExists()
        composeTestRule.onNodeWithText("输出模式").assertExists()
    }

    @Test
    fun bottomNavigation_canOpenAllPrimaryScreens() {
        composeTestRule.onNodeWithText("连接").performClick()
        composeTestRule.onNodeWithText("直播连接").assertExists()

        composeTestRule.onNodeWithText("输出").performClick()
        composeTestRule.onNodeWithText("输出配置").assertExists()

        composeTestRule.onNodeWithText("规则").performClick()
        composeTestRule.onNodeWithText("规则配置").assertExists()

        composeTestRule.onNodeWithText("日志").performClick()
        composeTestRule.onNodeWithText("事件日志").assertExists()

        composeTestRule.onNodeWithText("控制台").performClick()
        composeTestRule.onNodeWithText("直播控制台").assertExists()
    }
}
