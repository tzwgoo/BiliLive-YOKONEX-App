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
        composeTestRule.onNodeWithText("状态").assertExists()
        composeTestRule.onNodeWithText("监听状态").assertExists()
        composeTestRule.onNodeWithText("输出模式").assertExists()
    }

    @Test
    fun bottomNavigation_canOpenAllPrimaryScreens() {
        composeTestRule.onNodeWithText("直播间配置").performClick()
        composeTestRule.onNodeWithText("直播间配置").assertExists()

        composeTestRule.onNodeWithText("设备连接").performClick()
        composeTestRule.onNodeWithText("设备连接").assertExists()

        composeTestRule.onNodeWithText("规则配置").performClick()
        composeTestRule.onNodeWithText("规则配置").assertExists()

        composeTestRule.onNodeWithText("波形库").performClick()
        composeTestRule.onNodeWithText("波形编辑器").assertExists()

        composeTestRule.onNodeWithText("日志").performClick()
        composeTestRule.onNodeWithText("事件日志").assertExists()

        composeTestRule.onNodeWithText("状态").performClick()
        composeTestRule.onNodeWithText("状态").assertExists()
    }
}
