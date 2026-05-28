package com.yokonex.bililive.app.ui.waveforms

import kotlin.math.abs
import kotlin.math.roundToInt

fun strengthFromCanvasY(
    y: Float,
    height: Float,
): Int {
    if (height <= 0f) {
        return 0
    }
    val normalized = 1f - (y / height).coerceIn(0f, 1f)
    return (normalized * 180f).roundToInt().coerceIn(0, 180)
}

fun segmentIndexFromCanvasX(
    x: Float,
    segmentWidths: List<Float>,
): Int? {
    if (segmentWidths.isEmpty()) {
        return null
    }
    var cursor = 0f
    segmentWidths.forEachIndexed { index, width ->
        cursor += width
        if (x <= cursor) {
            return index
        }
    }
    return segmentWidths.lastIndex
}

fun insertIndexFromBoundaryX(
    x: Float,
    segmentWidths: List<Float>,
    tolerance: Float,
): Int? {
    if (segmentWidths.size < 2) {
        return null
    }
    var cursor = 0f
    for (index in 0 until segmentWidths.lastIndex) {
        cursor += segmentWidths[index]
        if (abs(x - cursor) <= tolerance) {
            return index + 1
        }
    }
    return null
}
