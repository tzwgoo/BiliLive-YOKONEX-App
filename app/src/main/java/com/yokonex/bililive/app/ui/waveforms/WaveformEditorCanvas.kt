package com.yokonex.bililive.app.ui.waveforms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
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
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val segmentDurations = waveform?.steps?.map { it.durationMs.coerceAtLeast(1) }.orEmpty()
    val currentWaveform by rememberUpdatedState(waveform)
    val currentEditable by rememberUpdatedState(editable)
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

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val minSegmentWidthPx = with(density) { 108.dp.toPx() }
        val widths = segmentWidths(
            viewportWidth = viewportWidthPx,
            segmentDurations = segmentDurations,
            minSegmentWidth = minSegmentWidthPx,
        )
        val canvasWidthPx = widths.sum()
        val canvasWidthDp = with(density) { canvasWidthPx.toDp() }

        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .width(canvasWidthDp)
                        .height(220.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(canvasBackground)
                        .pointerInput(editable) {
                            detectTapGestures { offset ->
                                if (!currentEditable) {
                                    return@detectTapGestures
                                }
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

            // 画布下方直接展示每一步的 A/B 强度，横向滚动时与上面的绘图区同步。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
            ) {
                Row(modifier = Modifier.width(canvasWidthDp)) {
                    waveform.steps.forEachIndexed { index, step ->
                        val segmentWidthDp = with(density) { widths[index].toDp() }
                        Column(
                            modifier = Modifier
                                .width(segmentWidthDp)
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "步骤 ${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "A ${step.channelA}",
                                style = MaterialTheme.typography.bodySmall,
                                color = channelAColor,
                            )
                            Text(
                                text = "B ${step.channelB}",
                                style = MaterialTheme.typography.bodySmall,
                                color = channelBColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun segmentWidths(
    viewportWidth: Float,
    segmentDurations: List<Int>,
    minSegmentWidth: Float,
): List<Float> {
    val totalDuration = segmentDurations.sum().coerceAtLeast(1)
    val baseWidth = maxOf(viewportWidth, minSegmentWidth * segmentDurations.size)
    return segmentDurations.map { duration ->
        maxOf(
            minSegmentWidth,
            baseWidth * (duration.toFloat() / totalDuration.toFloat()),
        )
    }
}

private fun strengthToCanvasY(strength: Int, height: Float): Float =
    height - (strength.coerceIn(0, 180) / 180f) * height
