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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
    val currentWaveform by rememberUpdatedState(waveform)
    val currentEditable by rememberUpdatedState(editable)
    val currentSegmentDurations by rememberUpdatedState(segmentDurations)
    val currentOnInsertStep by rememberUpdatedState(onInsertStep)
    val currentOnStrengthDrag by rememberUpdatedState(onStrengthDrag)

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
            .pointerInput(editable) {
                detectTapGestures { offset ->
                    if (!currentEditable) {
                        return@detectTapGestures
                    }
                    val widths = segmentWidths(
                        totalWidth = size.width.toFloat(),
                        segmentDurations = currentSegmentDurations,
                    )
                    insertIndexFromBoundaryX(
                        x = offset.x,
                        segmentWidths = widths,
                        tolerance = 18f,
                    )?.let(currentOnInsertStep)
                }
            }
            .pointerInput(editable) {
                var activeDrag: WaveformDragTarget? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        if (!currentEditable) {
                            return@detectDragGestures
                        }
                        val activeWaveform = currentWaveform ?: return@detectDragGestures
                        val widths = segmentWidths(
                            totalWidth = size.width.toFloat(),
                            segmentDurations = currentSegmentDurations,
                        )
                        val channelAYs = activeWaveform.steps.map { step ->
                            strengthToCanvasY(step.channelA, size.height.toFloat())
                        }
                        val channelBYs = activeWaveform.steps.map { step ->
                            strengthToCanvasY(step.channelB, size.height.toFloat())
                        }
                        val dragTarget = resolveDragTarget(
                            x = offset.x,
                            y = offset.y,
                            segmentWidths = widths,
                            channelAYs = channelAYs,
                            channelBYs = channelBYs,
                            handleRadius = 24f,
                        ) ?: return@detectDragGestures
                        activeDrag = dragTarget
                        currentOnStrengthDrag(
                            dragTarget.stepIndex,
                            dragTarget.channel,
                            strengthFromCanvasY(offset.y, size.height.toFloat()),
                        )
                    },
                    onDragEnd = {
                        activeDrag = null
                    },
                    onDragCancel = {
                        activeDrag = null
                    },
                    onDrag = { change, _ ->
                        val currentDrag = activeDrag ?: return@detectDragGestures
                        currentOnStrengthDrag(
                            currentDrag.stepIndex,
                            currentDrag.channel,
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
            val aHandleX = channelHandleX(startX, segmentWidth, WaveformChannel.A)
            val bHandleX = channelHandleX(startX, segmentWidth, WaveformChannel.B)
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
                strokeWidth = 6f,
            )
            drawCircle(
                color = channelAColor,
                radius = 11f,
                center = Offset(aHandleX, aY),
            )
            drawCircle(
                color = channelBColor,
                radius = 11f,
                center = Offset(bHandleX, bY),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = 15f,
                center = Offset(aHandleX, aY),
                style = Stroke(width = 2f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = 15f,
                center = Offset(bHandleX, bY),
                style = Stroke(width = 2f),
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
