package com.akocis.babysleeptracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
    maxMonths: Int = 36
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

    // Determine the visible range of months
    val lastMeasurementMonth = measurements.maxOfOrNull { it.monthAge } ?: 0.0
    val visibleMonths = maxOf(12, minOf(maxMonths, ((lastMeasurementMonth + 3) / 6).toInt() * 6 + 6))

    // Find Y range from percentile data within visible months
    val visiblePercentiles = percentileData.filter { it.monthAge <= visibleMonths }
    val minY = visiblePercentiles.minOf { it.p3 } * 0.95
    val maxY = visiblePercentiles.maxOf { it.p97 } * 1.05

    var selectedPoint by remember { mutableStateOf<MeasurementPoint?>(null) }

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

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(measurements) {
                    detectTapGestures { offset ->
                        if (measurements.isEmpty()) return@detectTapGestures
                        val leftPad = 40f
                        val rightPad = 16f
                        val topPad = 12f
                        val bottomPad = 28f
                        val chartW = size.width - leftPad - rightPad
                        val chartH = size.height - topPad - bottomPad

                        // Find closest measurement point
                        selectedPoint = measurements.minByOrNull { point ->
                            val px = leftPad + (point.monthAge / visibleMonths) * chartW
                            val py = topPad + (1 - (point.value - minY) / (maxY - minY)) * chartH
                            val dx = offset.x - px
                            val dy = offset.y - py
                            dx * dx + dy * dy
                        }?.let { point ->
                            val px = leftPad + (point.monthAge / visibleMonths) * chartW
                            val py = topPad + (1 - (point.value - minY) / (maxY - minY)) * chartH
                            val dx = offset.x - px
                            val dy = offset.y - py
                            if (dx * dx + dy * dy < 2500) point else null
                        }
                    }
                }
        ) {
            val leftPad = 40f
            val rightPad = 16f
            val topPad = 12f
            val bottomPad = 28f
            val chartW = size.width - leftPad - rightPad
            val chartH = size.height - topPad - bottomPad

            fun xFor(month: Double) = (leftPad + (month / visibleMonths) * chartW).toFloat()
            fun yFor(value: Double) = (topPad + (1 - (value - minY) / (maxY - minY)) * chartH).toFloat()

            // Draw grid lines
            val ySteps = 5
            val yRange = maxY - minY
            val yStep = yRange / ySteps
            for (i in 0..ySteps) {
                val v = minY + i * yStep
                val y = yFor(v)
                drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + chartW, y))
                drawContext.canvas.nativeCanvas.drawText(
                    "%.1f".format(v),
                    2f, y + 4f,
                    android.graphics.Paint().apply {
                        color = labelColor.hashCode()
                        textSize = 22f
                    }
                )
            }

            // Month grid
            val monthStep = if (visibleMonths <= 12) 3 else 6
            for (m in 0..visibleMonths step monthStep) {
                val x = xFor(m.toDouble())
                drawLine(gridColor, Offset(x, topPad), Offset(x, topPad + chartH))
                drawContext.canvas.nativeCanvas.drawText(
                    "${m}m",
                    x - 8f, topPad + chartH + 20f,
                    android.graphics.Paint().apply {
                        color = labelColor.hashCode()
                        textSize = 22f
                    }
                )
            }

            // Draw percentile curves
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
            for ((pIdx, accessor) in listOf<(PercentileRow) -> Double>(
                { it.p3 }, { it.p15 }, { it.p50 }, { it.p85 }, { it.p97 }
            ).withIndex()) {
                val path = Path()
                val filteredData = visiblePercentiles
                filteredData.forEachIndexed { i, row ->
                    val x = xFor(row.monthAge.toDouble())
                    val y = yFor(accessor(row))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val style = if (pIdx == 2) Stroke(width = 2.5f) else Stroke(width = 1.5f, pathEffect = dashEffect)
                drawPath(path, percentileColors[pIdx], style = style)

                // Label at right edge
                val lastRow = filteredData.last()
                val labelX = xFor(lastRow.monthAge.toDouble()) + 2f
                val labelY = yFor(accessor(lastRow))
                drawContext.canvas.nativeCanvas.drawText(
                    percentileLabels[pIdx],
                    labelX, labelY + 4f,
                    android.graphics.Paint().apply {
                        color = percentileColors[pIdx].hashCode()
                        textSize = 18f
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
                drawPath(mPath, accentColor, style = Stroke(width = 3f))

                sorted.forEach { point ->
                    val x = xFor(point.monthAge)
                    val y = yFor(point.value)
                    drawCircle(accentColor, 7f, Offset(x, y))
                    drawCircle(Color.White, 3.5f, Offset(x, y))
                }

                // Highlight selected
                selectedPoint?.let { point ->
                    val x = xFor(point.monthAge)
                    val y = yFor(point.value)
                    drawCircle(accentColor, 11f, Offset(x, y))
                    drawCircle(Color.White, 5f, Offset(x, y))
                }
            }
        }
    }
}
