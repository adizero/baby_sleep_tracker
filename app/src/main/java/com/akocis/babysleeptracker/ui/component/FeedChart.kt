package com.akocis.babysleeptracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.akocis.babysleeptracker.model.DayStats
import com.akocis.babysleeptracker.ui.theme.FeedColor
import java.time.format.DateTimeFormatter

@Composable
fun FeedChart(
    stats: List<DayStats>,
    movingAverage: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    val barColor = FeedColor
    val trendColor = Color(0xFFEF5350)
    val textColor = MaterialTheme.colorScheme.onSurface
    val labelFormatter = DateTimeFormatter.ofPattern("MM/dd")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp)
    ) {
        if (stats.isEmpty()) return@Canvas

        val maxMinutes = stats.maxOf { it.totalFeedDuration.toMinutes() }.coerceAtLeast(10)
        val maMax = if (movingAverage.isNotEmpty()) movingAverage.max() else 0f
        val chartMax = maxOf(maxMinutes.toFloat(), maMax * 60f).coerceAtLeast(10f)

        val barWidth = (size.width / stats.size) * 0.7f
        val gap = (size.width / stats.size) * 0.3f
        val bottomPadding = 40f
        val chartHeight = size.height - bottomPadding

        stats.forEachIndexed { index, dayStat ->
            val minutes = dayStat.totalFeedDuration.toMinutes().toFloat()
            val barHeight = (minutes / chartMax) * chartHeight
            val x = index * (barWidth + gap) + gap / 2

            drawRect(
                color = barColor,
                topLeft = Offset(x, chartHeight - barHeight),
                size = Size(barWidth, barHeight)
            )

            val label = if (minutes >= 60) {
                String.format("%.1fh", minutes / 60f)
            } else {
                String.format("%.0fm", minutes)
            }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x + barWidth / 2,
                chartHeight - barHeight - 8f,
                android.graphics.Paint().apply {
                    color = textColor.hashCode()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 28f
                }
            )

            drawContext.canvas.nativeCanvas.drawText(
                dayStat.label ?: dayStat.date.format(labelFormatter),
                x + barWidth / 2,
                size.height - 4f,
                android.graphics.Paint().apply {
                    color = textColor.hashCode()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 26f
                }
            )
        }

        // Draw moving average trend line
        if (movingAverage.size >= 2) {
            for (i in 0 until movingAverage.size - 1) {
                val x1 = i * (barWidth + gap) + gap / 2 + barWidth / 2
                val y1 = chartHeight - (movingAverage[i] * 60f / chartMax) * chartHeight
                val x2 = (i + 1) * (barWidth + gap) + gap / 2 + barWidth / 2
                val y2 = chartHeight - (movingAverage[i + 1] * 60f / chartMax) * chartHeight

                drawLine(
                    color = trendColor,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }

            movingAverage.forEachIndexed { i, value ->
                val x = i * (barWidth + gap) + gap / 2 + barWidth / 2
                val y = chartHeight - (value * 60f / chartMax) * chartHeight
                drawCircle(
                    color = trendColor,
                    radius = 6f,
                    center = Offset(x, y)
                )
            }
        }
    }
}
