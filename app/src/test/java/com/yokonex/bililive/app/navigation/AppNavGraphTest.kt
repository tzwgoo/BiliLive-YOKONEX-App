package com.yokonex.bililive.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavGraphTest {

    @Test
    fun appNavigationItems_usesExpectedMaterialIconMapping() {
        val items = appNavigationItems()

        assertEquals(
            listOf(
                NavigationItemSpec("dashboard", "主控台", NavigationIcon.Dashboard),
                NavigationItemSpec("events", "事件配置", NavigationIcon.Events),
                NavigationItemSpec("waveforms", "波形库", NavigationIcon.Waveforms),
            ),
            items,
        )
    }
}
