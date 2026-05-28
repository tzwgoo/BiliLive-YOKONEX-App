package com.yokonex.bililive.app.ui.waveforms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaveformCanvasGeometryTest {

    @Test
    fun strengthFromCanvasY_mapsTopTo180AndBottomTo0() {
        assertEquals(180, strengthFromCanvasY(y = 0f, height = 240f))
        assertEquals(90, strengthFromCanvasY(y = 120f, height = 240f))
        assertEquals(0, strengthFromCanvasY(y = 240f, height = 240f))
    }

    @Test
    fun insertIndexFromBoundaryX_returnsMiddleBoundaryIndex() {
        val index = insertIndexFromBoundaryX(
            x = 82f,
            segmentWidths = listOf(80f, 40f, 120f),
            tolerance = 8f,
        )

        assertEquals(1, index)
    }

    @Test
    fun resolveDragTarget_prefersBHandleWhenTouchNearBHandle() {
        val target = resolveDragTarget(
            x = 72f,
            y = 120f,
            segmentWidths = listOf(100f),
            channelAYs = listOf(120f),
            channelBYs = listOf(120f),
            handleRadius = 18f,
        )

        assertEquals(WaveformDragTarget(stepIndex = 0, channel = WaveformChannel.B), target)
    }

    @Test
    fun resolveDragTarget_returnsNullWhenNoSegments() {
        val target = resolveDragTarget(
            x = 20f,
            y = 30f,
            segmentWidths = emptyList(),
            channelAYs = emptyList(),
            channelBYs = emptyList(),
            handleRadius = 18f,
        )

        assertNull(target)
    }
}
