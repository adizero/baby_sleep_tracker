package com.akocis.babysleeptracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akocis.babysleeptracker.data.WhoGrowthData.PercentileRow
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class MeasurementPoint(
    val monthAge: Double,
    val value: Double,
    val label: String
)

@Composable
fun GrowthChart(
    title: String,
    unit: String,
    percentileData: List<PercentileRow>,
    measurements: List<MeasurementPoint>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    maxMonths: Int = 36,
    isFullscreen: Boolean = false,
    onDoubleClick: (() -> Unit)? = null
) {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val percentileColors = listOf(
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
    val percentileLabels = listOf("3rd", "15th", "50th", "85th", "97th")

    val lastMeasurementMonth = measurements.maxOfOrNull { it.monthAge } ?: 0.0
    val visibleMonths = maxOf(12, minOf(maxMonths, ((lastMeasurementMonth + 3) / 6).toInt() * 6 + 6))

    val visiblePercentiles = percentileData.filter { it.monthAge <= visibleMonths }
    val baseMinY = visiblePercentiles.minOf { it.p3 } * 0.95
    val baseMaxY = visiblePercentiles.maxOf { it.p97 } * 1.05

    var selectedPoint by remember { mutableStateOf<MeasurementPoint?>(null) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        selectedPoint?.let { point ->
            Text(
                text = "${point.label}: ${"%.1f".format(point.value)} $unit",
                style = MaterialTheme.typography.bodySmall,
                color = accentColor,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        val chartModifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(220.dp)

        Canvas(
            modifier = chartModifier
                .pointerInput(measurements, scale, offsetX, offsetY) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (measurements.isEmpty()) return@detectTapGestures
                            val leftPad = 40f
                            val rightPad = 16f
                            val topPad = 12f
                            val bottomPad = 28f
                            val chartW = size.width - leftPad - rightPad
                            val chartH = size.height - topPad - bottomPad

                            selectedPoint = measurements.minByOrNull { point ->
                                val px = (leftPad + (point.monthAge / visibleMonths).toFloat() * chartW) * scale + offsetX
                                val py = (topPad + (1 - (point.value - baseMinY) / (baseMaxY - baseMinY)).toFloat() * chartH) * scale + offsetY
                                val dx = offset.x - px
                                val dy = offset.y - py
                                dx * dx + dy * dy
                            }?.let { point ->
                                val px = (leftPad + (point.monthAge / visibleMonths).toFloat() * chartW) * scale + offsetX
                                val py = (topPad + (1 - (point.value - baseMinY) / (baseMaxY - baseMinY)).toFloat() * chartH) * scale + offsetY
                                val dx = offset.x - px
                                val dy = offset.y - py
                                if (dx * dx + dy * dy < 2500 * scale * scale) point else null
                            }
                        },
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                onDoubleClick?.invoke()
                            }
                        }
                    )
                }
                .pointerInput(scale) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isZoomed = scale > 1.01f

                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes

                            if (pointers.size >= 2) {
                                // Multi-finger: always handle zoom/pan
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()

                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                offsetX = centroid.x - (centroid.x - offsetX) * (newScale / scale) + pan.x
                                offsetY = centroid.y - (centroid.y - offsetY) * (newScale / scale) + pan.y
                                scale = newScale
                                val maxOffX = (size.width * (scale - 1))
                                val maxOffY = (size.height * (scale - 1))
                                offsetX = offsetX.coerceIn(-maxOffX, 0f)
                                offsetY = offsetY.coerceIn(-maxOffY, 0f)

                                pointers.forEach { it.consume() }
                                isZoomed = scale > 1.01f
                            } else if (pointers.size == 1 && isZoomed) {
                                // Single finger pan only when zoomed
                                val change = pointers[0]
                                if (change.positionChanged()) {
                                    val pan = change.position - change.previousPosition
                                    offsetX = (offsetX + pan.x).coerceIn(-(size.width * (scale - 1)), 0f)
                                    offsetY = (offsetY + pan.y).coerceIn(-(size.height * (scale - 1)), 0f)
                                    change.consume()
                                }
                            }
                            // Single finger when NOT zoomed: don't consume, let parent scroll
                        } while (pointers.any { it.pressed })
                    }
                }
        ) {
            val leftPad = 40f
            val rightPad = if (isFullscreen) 40f else 16f
            val topPad = 12f
            val bottomPad = if (isFullscreen) 48f else 28f
            val chartW = size.width - leftPad - rightPad
            val chartH = size.height - topPad - bottomPad

            fun xFor(month: Double) = ((leftPad + (month / visibleMonths) * chartW) * scale + offsetX).toFloat()
            fun yFor(value: Double) = ((topPad + (1 - (value - baseMinY) / (baseMaxY - baseMinY)) * chartH) * scale + offsetY).toFloat()

            clipRect {
                val textScale = scale.coerceAtMost(2f)
                val textPaint = android.graphics.Paint().apply {
                    color = labelColor.hashCode()
                    textSize = 22f * textScale
                }

                // Adaptive Y-axis labels based on zoom
                val yRange = baseMaxY - baseMinY
                val visibleYRange = yRange / scale
                val yStepRaw = visibleYRange / 5.0
                val magnitude = 10.0.pow(floor(log10(yStepRaw)))
                val normalized = yStepRaw / magnitude
                val niceStep = when {
                    normalized <= 1.0 -> 1.0
                    normalized <= 2.0 -> 2.0
                    normalized <= 5.0 -> 5.0
                    else -> 10.0
                } * magnitude

                val yStart = floor(baseMinY / niceStep) * niceStep
                val yEnd = ceil(baseMaxY / niceStep) * niceStep
                var v = yStart
                while (v <= yEnd) {
                    val y = yFor(v)
                    if (y >= -50f && y <= size.height + 50f) {
                        drawLine(gridColor, Offset(xFor(0.0), y), Offset(xFor(visibleMonths.toDouble()), y))
                        drawContext.canvas.nativeCanvas.drawText(
                            "%.1f".format(v),
                            2f * scale + offsetX, y + 4f,
                            textPaint
                        )
                    }
                    v += niceStep
                }

                // Adaptive X-axis labels based on zoom
                val visibleMonthRange = visibleMonths.toDouble() / scale
                val xStepMonths: Double
                val useWeeks: Boolean
                when {
                    visibleMonthRange <= 3.0 -> { xStepMonths = 0.25; useWeeks = true }
                    visibleMonthRange <= 6.0 -> { xStepMonths = 0.5; useWeeks = true }
                    visibleMonthRange <= 12.0 -> { xStepMonths = 1.0; useWeeks = false }
                    visibleMonthRange <= 18.0 -> { xStepMonths = 2.0; useWeeks = false }
                    visibleMonthRange <= 24.0 -> { xStepMonths = 3.0; useWeeks = false }
                    else -> { xStepMonths = 6.0; useWeeks = false }
                }

                // X-axis labels at fixed position below chart, not affected by zoom offset
                val xLabelY = size.height - bottomPad + 20f
                var m = 0.0
                while (m <= visibleMonths) {
                    val x = xFor(m)
                    if (x >= -50f && x <= size.width + 50f) {
                        drawLine(gridColor, Offset(x, yFor(baseMaxY)), Offset(x, yFor(baseMinY)))
                        val label = if (useWeeks) {
                            val weeks = (m * 30.4375 / 7).toInt()
                            "${weeks}w"
                        } else {
                            "${m.toInt()}m"
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            x - 8f * textScale, xLabelY,
                            textPaint
                        )
                    }
                    m += xStepMonths
                }

                // Draw percentile curves
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                for ((pIdx, accessor) in listOf<(PercentileRow) -> Double>(
                    { it.p3 }, { it.p15 }, { it.p50 }, { it.p85 }, { it.p97 }
                ).withIndex()) {
                    val path = Path()
                    visiblePercentiles.forEachIndexed { i, row ->
                        val x = xFor(row.monthAge.toDouble())
                        val y = yFor(accessor(row))
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val strokeW = if (pIdx == 2) 2.5f else 1.5f
                    val style = if (pIdx == 2) Stroke(width = strokeW * textScale)
                                else Stroke(width = strokeW * textScale, pathEffect = dashEffect)
                    drawPath(path, percentileColors[pIdx], style = style)

                    val lastRow = visiblePercentiles.last()
                    val labelX = xFor(lastRow.monthAge.toDouble()) + 2f
                    val labelY = yFor(accessor(lastRow))
                    drawContext.canvas.nativeCanvas.drawText(
                        percentileLabels[pIdx],
                        labelX, labelY + 4f,
                        android.graphics.Paint().apply {
                            color = percentileColors[pIdx].hashCode()
                            textSize = 18f * textScale
                        }
                    )
                }

                // Draw measurement points and line
                if (measurements.isNotEmpty()) {
                    val sorted = measurements.sortedBy { it.monthAge }
                    val mPath = Path()
                    sorted.forEachIndexed { i, point ->
                        val x = xFor(point.monthAge)
                        val y = yFor(point.value)
                        if (i == 0) mPath.moveTo(x, y) else mPath.lineTo(x, y)
                    }
                    drawPath(mPath, accentColor, style = Stroke(width = 3f * textScale))

                    sorted.forEach { point ->
                        val x = xFor(point.monthAge)
                        val y = yFor(point.value)
                        val dotSize = 7f * textScale
                        drawCircle(accentColor, dotSize, Offset(x, y))
                        drawCircle(Color.White, dotSize / 2f, Offset(x, y))
                    }

                    selectedPoint?.let { point ->
                        val x = xFor(point.monthAge)
                        val y = yFor(point.value)
                        val dotSize = 11f * textScale
                        drawCircle(accentColor, dotSize, Offset(x, y))
                        drawCircle(Color.White, dotSize / 2f, Offset(x, y))
                    }
                }
            }
        }
    }
}
