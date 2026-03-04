package com.akocis.babysleeptracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.Duration

private val DayColor = Color(0xFFFFD54F)
private val NightColor = Color(0xFF5C6BC0)

@Composable
fun SleepPieChart(
    daySleep: Duration,
    nightSleep: Duration,
    modifier: Modifier = Modifier
) {
    val totalMinutes = daySleep.toMinutes() + nightSleep.toMinutes()
    if (totalMinutes == 0L) return

    val dayAngle = (daySleep.toMinutes().toFloat() / totalMinutes) * 360f
    val nightAngle = 360f - dayAngle

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(180.dp)
        ) {
            Canvas(modifier = Modifier.size(160.dp)) {
                // Day sleep arc
                drawArc(
                    color = DayColor,
                    startAngle = -90f,
                    sweepAngle = dayAngle,
                    useCenter = true,
                    size = size
                )

                // Night sleep arc
                drawArc(
                    color = NightColor,
                    startAngle = -90f + dayAngle,
                    sweepAngle = nightAngle,
                    useCenter = true,
                    size = size
                )

                // White center circle for donut effect
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension / 4f
                )
            }
        }

        // Legend
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawRect(color = DayColor, size = size)
                }
                Text(
                    text = "Day: ${formatDur(daySleep)}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawRect(color = NightColor, size = size)
                }
                Text(
                    text = "Night: ${formatDur(nightSleep)}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private fun formatDur(d: Duration): String {
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return "${h}h ${m}m"
}
