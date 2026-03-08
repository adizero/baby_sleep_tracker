package com.akocis.babysleeptracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.akocis.babysleeptracker.model.DayStats
import com.akocis.babysleeptracker.ui.theme.FeedLeftColor
import com.akocis.babysleeptracker.ui.theme.FeedRightColor
import java.time.format.DateTimeFormatter

@Composable
fun FeedChart(
    stats: List<DayStats>,
    movingAverage: List<Float> = emptyList(),
    highlightIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    val trendColor = Color(0xFFEF5350)
    val highlightColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val labelFormatter = DateTimeFormatter.ofPattern("MM/dd")

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minBarSlot = 40.dp
        val contentWidth = (minBarSlot * stats.size).coerceAtLeast(maxWidth)
        val scrollState = rememberScrollState()
        LaunchedEffect(stats.size) { scrollState.scrollTo(scrollState.maxValue) }

        Canvas(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .width(contentWidth)
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
                val x = index * (barWidth + gap) + gap / 2
                var currentY = chartHeight

                val leftMinutes = dayStat.leftFeedDuration.toMinutes().toFloat()
                val rightMinutes = dayStat.rightFeedDuration.toMinutes().toFloat()

                // Left segment (bottom)
                if (leftMinutes > 0) {
                    val segHeight = (leftMinutes / chartMax) * chartHeight
                    currentY -= segHeight
                    drawRect(
                        color = FeedLeftColor,
                        topLeft = Offset(x, currentY),
                        size = Size(barWidth, segHeight)
                    )
                }

                // Right segment (top)
                if (rightMinutes > 0) {
                    val segHeight = (rightMinutes / chartMax) * chartHeight
                    currentY -= segHeight
                    drawRect(
                        color = FeedRightColor,
                        topLeft = Offset(x, currentY),
                        size = Size(barWidth, segHeight)
                    )
                }

                // Highlight current period
                val totalMinutes = dayStat.totalFeedDuration.toMinutes().toFloat()
                if (index == highlightIndex && totalMinutes > 0) {
                    val totalBarHeight = chartHeight - currentY
                    drawRect(
                        color = highlightColor,
                        topLeft = Offset(x - 1f, currentY - 1f),
                        size = Size(barWidth + 2f, totalBarHeight + 2f),
                        style = Stroke(width = 3f)
                    )
                }

                // Duration label on top
                if (totalMinutes > 0) {
                    val label = if (totalMinutes >= 60) {
                        String.format("%.1fh", totalMinutes / 60f)
                    } else {
                        String.format("%.0fm", totalMinutes)
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        x + barWidth / 2,
                        currentY - 8f,
                        android.graphics.Paint().apply {
                            color = textColor.hashCode()
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 28f
                        }
                    )
                }

                // Date/period label
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
                    val cx = i * (barWidth + gap) + gap / 2 + barWidth / 2
                    val cy = chartHeight - (value * 60f / chartMax) * chartHeight
                    drawCircle(
                        color = trendColor,
                        radius = 6f,
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }

    // Legend
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        LegendItem(color = FeedLeftColor, label = "Left")
        LegendItem(color = FeedRightColor, label = "Right")
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawRect(color = color, size = size)
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
