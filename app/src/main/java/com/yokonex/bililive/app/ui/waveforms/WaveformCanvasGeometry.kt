package com.yokonex.bililive.app.ui.waveforms

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

data class WaveformDragTarget(
    val stepIndex: Int,
    val channel: WaveformChannel,
)

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

fun resolveDragTarget(
    x: Float,
    y: Float,
    segmentWidths: List<Float>,
    channelAYs: List<Float>,
    channelBYs: List<Float>,
    handleRadius: Float,
): WaveformDragTarget? {
    val stepIndex = segmentIndexFromCanvasX(x, segmentWidths) ?: return null
    val segmentWidth = segmentWidths.getOrNull(stepIndex) ?: return null
    val channelAY = channelAYs.getOrNull(stepIndex) ?: return null
    val channelBY = channelBYs.getOrNull(stepIndex) ?: return null
    val segmentStartX = segmentWidths.take(stepIndex).sum()
    val aHandleX = channelHandleX(segmentStartX, segmentWidth, WaveformChannel.A)
    val bHandleX = channelHandleX(segmentStartX, segmentWidth, WaveformChannel.B)
    val aDistance = hypot(x - aHandleX, y - channelAY)
    val bDistance = hypot(x - bHandleX, y - channelBY)

    if (aDistance <= handleRadius || bDistance <= handleRadius) {
        val channel = if (aDistance <= bDistance) WaveformChannel.A else WaveformChannel.B
        return WaveformDragTarget(stepIndex = stepIndex, channel = channel)
    }

    val channel = if (abs(y - channelAY) <= abs(y - channelBY)) WaveformChannel.A else WaveformChannel.B
    return WaveformDragTarget(stepIndex = stepIndex, channel = channel)
}

fun channelHandleX(
    segmentStartX: Float,
    segmentWidth: Float,
    channel: WaveformChannel,
): Float =
    when (channel) {
        WaveformChannel.A -> segmentStartX + segmentWidth * 0.35f
        WaveformChannel.B -> segmentStartX + segmentWidth * 0.65f
    }
