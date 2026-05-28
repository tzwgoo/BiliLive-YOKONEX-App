package com.yokonex.bililive.app.ui.waveforms

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yokonex.bililive.domain.model.WaveformDefinition
import kotlin.math.abs

@Composable
fun WaveformEditorCanvas(
    waveform: WaveformDefinition?,
    editable: Boolean,
    onStrengthDrag: (Int, WaveformChannel, Int) -> Unit,
    onInsertStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canvasBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val channelAColor = MaterialTheme.colorScheme.primary
    val channelBColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val segmentDurations = waveform?.steps?.map { it.durationMs.coerceAtLeast(1) }.orEmpty()

    if (waveform == null || waveform.steps.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.large)
                .background(canvasBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "选择一个波形后开始编辑。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(MaterialTheme.shapes.large)
            .background(canvasBackground)
            .pointerInput(waveform, editable) {
                detectTapGestures { offset ->
                    if (!editable) {
                        return@detectTapGestures
                    }
                    val widths = segmentWidths(
                        totalWidth = size.width.toFloat(),
                        segmentDurations = segmentDurations,
                    )
                    insertIndexFromBoundaryX(
                        x = offset.x,
                        segmentWidths = widths,
                        tolerance = 18f,
                    )?.let(onInsertStep)
                }
            }
            .pointerInput(waveform, editable) {
                var activeDrag: Pair<Int, WaveformChannel>? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        if (!editable) {
                            return@detectDragGestures
                        }
                        val widths = segmentWidths(
                            totalWidth = size.width.toFloat(),
                            segmentDurations = segmentDurations,
                        )
                        val stepIndex = segmentIndexFromCanvasX(offset.x, widths) ?: return@detectDragGestures
                        val step = waveform.steps[stepIndex]
                        val aY = strengthToCanvasY(step.channelA, size.height.toFloat())
                        val bY = strengthToCanvasY(step.channelB, size.height.toFloat())
                        val channel = if (abs(offset.y - aY) <= abs(offset.y - bY)) {
                            WaveformChannel.A
                        } else {
                            WaveformChannel.B
                        }
                        activeDrag = stepIndex to channel
                        onStrengthDrag(stepIndex, channel, strengthFromCanvasY(offset.y, size.height.toFloat()))
                    },
                    onDragEnd = {
                        activeDrag = null
                    },
                    onDragCancel = {
                        activeDrag = null
                    },
                    onDrag = { change, _ ->
                        val currentDrag = activeDrag ?: return@detectDragGestures
                        onStrengthDrag(
                            currentDrag.first,
                            currentDrag.second,
                            strengthFromCanvasY(change.position.y, size.height.toFloat()),
                        )
                        change.consume()
                    },
                )
            },
    ) {
        val widths = segmentWidths(
            totalWidth = size.width,
            segmentDurations = segmentDurations,
        )
        var startX = 0f
        waveform.steps.forEachIndexed { index, step ->
            val segmentWidth = widths[index]
            val endX = startX + segmentWidth
            drawRoundRect(
                color = outlineColor.copy(alpha = 0.18f),
                topLeft = Offset(startX, 0f),
                size = androidx.compose.ui.geometry.Size(segmentWidth, size.height),
                cornerRadius = CornerRadius(18f, 18f),
                style = Stroke(width = 1.2f),
            )
            val aY = strengthToCanvasY(step.channelA, size.height)
            val bY = strengthToCanvasY(step.channelB, size.height)
            drawLine(
                color = channelAColor,
                start = Offset(startX, aY),
                end = Offset(endX, aY),
                strokeWidth = 6f,
            )
            drawLine(
                color = channelBColor,
                start = Offset(startX, bY),
                end = Offset(endX, bY),
                strokeWidth = 4f,
            )
            drawCircle(
                color = channelAColor,
                radius = 10f,
                center = Offset((startX + endX) / 2f, aY),
            )
            drawCircle(
                color = channelBColor,
                radius = 8f,
                center = Offset((startX + endX) / 2f, bY),
            )
            if (index < waveform.steps.lastIndex) {
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(endX, 24f),
                    end = Offset(endX, size.height - 24f),
                    strokeWidth = 2f,
                )
            }
            startX = endX
        }
    }
}

private fun segmentWidths(
    totalWidth: Float,
    segmentDurations: List<Int>,
): List<Float> {
    val totalDuration = segmentDurations.sum().coerceAtLeast(1)
    return segmentDurations.map { duration ->
        totalWidth * (duration.toFloat() / totalDuration.toFloat())
    }
}

private fun strengthToCanvasY(strength: Int, height: Float): Float =
    height - (strength.coerceIn(0, 180) / 180f) * height
