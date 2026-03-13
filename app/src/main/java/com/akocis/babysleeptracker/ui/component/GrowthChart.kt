package com.akocis.babysleeptracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akocis.babysleeptracker.data.WhoGrowthData.PercentileRow

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

        val chartHeight = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(220.dp)

        Canvas(
            modifier = chartHeight
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
                                // Reset zoom
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                onDoubleClick?.invoke()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        // Adjust offset so zoom centers on the pinch point
                        offsetX = centroid.x - (centroid.x - offsetX) * (newScale / scale) + pan.x
                        offsetY = centroid.y - (centroid.y - offsetY) * (newScale / scale) + pan.y
                        scale = newScale
                        // Clamp offset
                        val maxOffX = (size.width * (scale - 1))
                        val maxOffY = (size.height * (scale - 1))
                        offsetX = offsetX.coerceIn(-maxOffX, 0f)
                        offsetY = offsetY.coerceIn(-maxOffY, 0f)
                    }
                }
        ) {
            val leftPad = 40f
            val rightPad = 16f
            val topPad = 12f
            val bottomPad = 28f
            val chartW = size.width - leftPad - rightPad
            val chartH = size.height - topPad - bottomPad

            fun xFor(month: Double) = ((leftPad + (month / visibleMonths) * chartW) * scale + offsetX).toFloat()
            fun yFor(value: Double) = ((topPad + (1 - (value - baseMinY) / (baseMaxY - baseMinY)) * chartH) * scale + offsetY).toFloat()

            clipRect {
                // Draw grid lines
                val ySteps = 5
                val yRange = baseMaxY - baseMinY
                val yStep = yRange / ySteps
                val textPaint = android.graphics.Paint().apply {
                    color = labelColor.hashCode()
                    textSize = 22f * scale.coerceAtMost(2f)
                }
                for (i in 0..ySteps) {
                    val v = baseMinY + i * yStep
                    val y = yFor(v)
                    drawLine(gridColor, Offset(xFor(0.0), y), Offset(xFor(visibleMonths.toDouble()), y))
                    drawContext.canvas.nativeCanvas.drawText(
                        "%.1f".format(v),
                        2f * scale + offsetX, y + 4f,
                        textPaint
                    )
                }

                // Month grid
                val monthStep = if (visibleMonths <= 12) 3 else 6
                for (m in 0..visibleMonths step monthStep) {
                    val x = xFor(m.toDouble())
                    drawLine(gridColor, Offset(x, yFor(baseMaxY)), Offset(x, yFor(baseMinY)))
                    drawContext.canvas.nativeCanvas.drawText(
                        "${m}m",
                        x - 8f * scale.coerceAtMost(2f), yFor(baseMinY) + 20f * scale.coerceAtMost(2f),
                        textPaint
                    )
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
                    val style = if (pIdx == 2) Stroke(width = strokeW * scale.coerceAtMost(2f)) else Stroke(width = strokeW * scale.coerceAtMost(2f), pathEffect = dashEffect)
                    drawPath(path, percentileColors[pIdx], style = style)

                    // Label at right edge
                    val lastRow = visiblePercentiles.last()
                    val labelX = xFor(lastRow.monthAge.toDouble()) + 2f
                    val labelY = yFor(accessor(lastRow))
                    drawContext.canvas.nativeCanvas.drawText(
                        percentileLabels[pIdx],
                        labelX, labelY + 4f,
                        android.graphics.Paint().apply {
                            color = percentileColors[pIdx].hashCode()
                            textSize = 18f * scale.coerceAtMost(2f)
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
                    drawPath(mPath, accentColor, style = Stroke(width = 3f * scale.coerceAtMost(2f)))

                    sorted.forEach { point ->
                        val x = xFor(point.monthAge)
                        val y = yFor(point.value)
                        val dotSize = 7f * scale.coerceAtMost(2f)
                        drawCircle(accentColor, dotSize, Offset(x, y))
                        drawCircle(Color.White, dotSize / 2f, Offset(x, y))
                    }

                    // Highlight selected
                    selectedPoint?.let { point ->
                        val x = xFor(point.monthAge)
                        val y = yFor(point.value)
                        val dotSize = 11f * scale.coerceAtMost(2f)
                        drawCircle(accentColor, dotSize, Offset(x, y))
                        drawCircle(Color.White, dotSize / 2f, Offset(x, y))
                    }
                }
            }
        }
    }
}
