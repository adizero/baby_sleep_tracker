package com.akocis.babysleeptracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.akocis.babysleeptracker.model.DayStats
import com.akocis.babysleeptracker.ui.theme.PeeColor
import com.akocis.babysleeptracker.ui.theme.PeePooColor
import com.akocis.babysleeptracker.ui.theme.PooColor
import java.time.format.DateTimeFormatter

@Composable
fun DiaperChart(
    stats: List<DayStats>,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val labelFormatter = DateTimeFormatter.ofPattern("MM/dd")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 16.dp)
    ) {
        if (stats.isEmpty()) return@Canvas

        val maxDiapers = stats.maxOf { it.totalDiapers }.coerceAtLeast(1)
        val barWidth = (size.width / stats.size) * 0.7f
        val gap = (size.width / stats.size) * 0.3f
        val bottomPadding = 40f
        val chartHeight = size.height - bottomPadding

        stats.forEachIndexed { index, dayStat ->
            val x = index * (barWidth + gap) + gap / 2
            var currentY = chartHeight

            // Pee segment
            if (dayStat.peeCount > 0) {
                val segHeight = (dayStat.peeCount.toFloat() / maxDiapers) * chartHeight
                currentY -= segHeight
                drawRect(
                    color = PeeColor,
                    topLeft = Offset(x, currentY),
                    size = Size(barWidth, segHeight)
                )
            }

            // Poo segment
            if (dayStat.pooCount > 0) {
                val segHeight = (dayStat.pooCount.toFloat() / maxDiapers) * chartHeight
                currentY -= segHeight
                drawRect(
                    color = PooColor,
                    topLeft = Offset(x, currentY),
                    size = Size(barWidth, segHeight)
                )
            }

            // PeePoo segment
            if (dayStat.peepooCount > 0) {
                val segHeight = (dayStat.peepooCount.toFloat() / maxDiapers) * chartHeight
                currentY -= segHeight
                drawRect(
                    color = PeePooColor,
                    topLeft = Offset(x, currentY),
                    size = Size(barWidth, segHeight)
                )
            }

            // Total count on top
            if (dayStat.totalDiapers > 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${dayStat.totalDiapers}",
                    x + barWidth / 2,
                    currentY - 8f,
                    android.graphics.Paint().apply {
                        color = textColor.hashCode()
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 28f
                    }
                )
            }

            // Date label
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
    }

    // Legend
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        LegendItem(color = PeeColor, label = "Pee")
        LegendItem(color = PooColor, label = "Poo")
        LegendItem(color = PeePooColor, label = "Both")
    }
}

@Composable
private fun LegendItem(
    color: androidx.compose.ui.graphics.Color,
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
