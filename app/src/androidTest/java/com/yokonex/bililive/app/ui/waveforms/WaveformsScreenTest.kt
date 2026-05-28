package com.yokonex.bililive.app.ui.waveforms

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokonex.bililive.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaveformsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun waveformScreen_showsLibraryAndEditorSections() {
        composeTestRule.onNodeWithText("波形库").performClick()
        composeTestRule.onNodeWithText("波形库列表").assertExists()
        composeTestRule.onNodeWithText("波形编辑器").assertExists()
        composeTestRule.onNodeWithText("保存波形").assertExists()
    }
}
