package com.yokonex.bililive.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavGraphTest {

    @Test
    fun appNavigationItems_usesExpectedMaterialIconMapping() {
        val items = appNavigationItems()

        assertEquals(
            listOf(
                NavigationItemSpec("dashboard", "状态", NavigationIcon.Dashboard),
                NavigationItemSpec("live", "直播配置", NavigationIcon.Live),
                NavigationItemSpec("output", "设备连接", NavigationIcon.Output),
                NavigationItemSpec("rules", "规则配置", NavigationIcon.Rules),
                NavigationItemSpec("waveforms", "波形库", NavigationIcon.Waveforms),
                NavigationItemSpec("logs", "日志", NavigationIcon.Logs),
            ),
            items,
        )
    }
}
