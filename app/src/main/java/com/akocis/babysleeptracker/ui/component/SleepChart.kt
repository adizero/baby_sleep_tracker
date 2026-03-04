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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.akocis.babysleeptracker.model.DayStats
import com.akocis.babysleeptracker.ui.theme.SleepButtonColor
import java.time.format.DateTimeFormatter

@Composable
fun SleepChart(
    stats: List<DayStats>,
    modifier: Modifier = Modifier
) {
    val barColor = SleepButtonColor
    val textColor = MaterialTheme.colorScheme.onSurface
    val labelFormatter = DateTimeFormatter.ofPattern("MM/dd")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp)
    ) {
        if (stats.isEmpty()) return@Canvas

        val maxMinutes = stats.maxOf { it.totalSleep.toMinutes() }.coerceAtLeast(60)
        val barWidth = (size.width / stats.size) * 0.7f
        val gap = (size.width / stats.size) * 0.3f
        val bottomPadding = 40f
        val chartHeight = size.height - bottomPadding

        stats.forEachIndexed { index, dayStat ->
            val minutes = dayStat.totalSleep.toMinutes().toFloat()
            val barHeight = (minutes / maxMinutes) * chartHeight
            val x = index * (barWidth + gap) + gap / 2

            // Draw bar
            drawRect(
                color = barColor,
                topLeft = Offset(x, chartHeight - barHeight),
                size = Size(barWidth, barHeight)
            )

            // Draw hours label on top of bar
            val hours = minutes / 60f
            val hoursLabel = String.format("%.1fh", hours)
            drawContext.canvas.nativeCanvas.drawText(
                hoursLabel,
                x + barWidth / 2,
                chartHeight - barHeight - 8f,
                android.graphics.Paint().apply {
                    color = textColor.hashCode()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 28f
                }
            )

            // Draw date label below bar
            drawContext.canvas.nativeCanvas.drawText(
                dayStat.date.format(labelFormatter),
                x + barWidth / 2,
                size.height - 4f,
                android.graphics.Paint().apply {
                    color = textColor.hashCode()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 26f
                }
            )
        }
    }
}
