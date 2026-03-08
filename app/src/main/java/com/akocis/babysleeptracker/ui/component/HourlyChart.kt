package com.akocis.babysleeptracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.akocis.babysleeptracker.model.DayStats
import java.time.Duration

@Composable
fun HourlyChart(
    stats: List<DayStats>,
    valueAccessor: (DayStats) -> Duration,
    barColor: Color,
    dayStartHour: Int = 7,
    dayEndHour: Int = 19,
    highlightHour: Int = -1,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val nightShade = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val highlightColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 16.dp)
    ) {
        if (stats.size != 24) return@Canvas

        val maxMinutes = stats.maxOf { valueAccessor(it).toMinutes() }.coerceAtLeast(1)
        val bottomPadding = 36f
        val chartHeight = size.height - bottomPadding
        val slotWidth = size.width / 24f
        val barWidth = slotWidth * 0.75f
        val barGap = (slotWidth - barWidth) / 2f

        // Draw night-hour background shading
        for (hour in 0 until 24) {
            val isNight = if (dayStartHour < dayEndHour) {
                hour < dayStartHour || hour >= dayEndHour
            } else {
                hour >= dayEndHour && hour < dayStartHour
            }
            if (isNight) {
                drawRect(
                    color = nightShade,
                    topLeft = Offset(hour * slotWidth, 0f),
                    size = Size(slotWidth, chartHeight)
                )
            }
        }

        // Draw bars
        stats.forEachIndexed { hour, dayStat ->
            val minutes = valueAccessor(dayStat).toMinutes().toFloat()
            val barHeight = (minutes / maxMinutes) * chartHeight
            val x = hour * slotWidth + barGap

            if (barHeight > 0f) {
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, chartHeight - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }

            // Highlight current hour with outline
            if (hour == highlightHour) {
                val hlY = if (barHeight > 0f) chartHeight - barHeight else chartHeight - 2f
                val hlH = if (barHeight > 0f) barHeight else 2f
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(x - 1f, hlY - 1f),
                    size = Size(barWidth + 2f, hlH + 2f),
                    cornerRadius = CornerRadius(2f),
                    style = Stroke(width = 3f)
                )
            }

            // Value label above bar
            if (minutes > 0) {
                val label = if (minutes >= 60) String.format("%.1fh", minutes / 60f)
                else String.format("%.0fm", minutes)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x + barWidth / 2,
                    chartHeight - barHeight - 4f,
                    android.graphics.Paint().apply {
                        color = textColor.hashCode()
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 20f
                    }
                )
            }

            // Hour label below
            val isKey = hour % 6 == 0
            val isCurrent = hour == highlightHour
            drawContext.canvas.nativeCanvas.drawText(
                hour.toString(),
                x + barWidth / 2,
                size.height - 4f,
                android.graphics.Paint().apply {
                    color = if (isCurrent) highlightColor.hashCode() else textColor.hashCode()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = if (isKey || isCurrent) 24f else 18f
                    isFakeBoldText = isKey || isCurrent
                }
            )
        }
    }
}
