package com.akocis.babysleeptracker.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.ui.theme.BathColor
import com.akocis.babysleeptracker.ui.theme.FeedColor
import com.akocis.babysleeptracker.ui.theme.PeeColor
import com.akocis.babysleeptracker.ui.theme.PooColor
import com.akocis.babysleeptracker.ui.theme.SleepButtonColor
import com.akocis.babysleeptracker.ui.theme.StrollerColor
import com.akocis.babysleeptracker.util.DateTimeUtil
import com.akocis.babysleeptracker.viewmodel.CalendarDayData
import com.akocis.babysleeptracker.viewmodel.CalendarViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onBack: () -> Unit
) {
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val calendarData by viewModel.calendarData.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Month navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(Icons.Default.ChevronLeft, "Previous month")
                }
                Text(
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.Default.ChevronRight, "Next month")
                }
            }

            // Day of week headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                val daysOfWeek = listOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
                )
                daysOfWeek.forEach { day ->
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Calendar grid
            val firstDay = currentMonth.atDay(1)
            // Monday = 1, so offset = dayOfWeek.value - 1
            val startOffset = firstDay.dayOfWeek.value - 1
            val daysInMonth = currentMonth.lengthOfMonth()
            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - startOffset + 1

                        if (dayNum in 1..daysInMonth) {
                            val date = currentMonth.atDay(dayNum)
                            val dayData = calendarData[date]
                            val isSelected = selectedDay?.date == date
                            val isToday = date == LocalDate.now()

                            CalendarDayCell(
                                dayNum = dayNum,
                                dayData = dayData,
                                isSelected = isSelected,
                                isToday = isToday,
                                onClick = { viewModel.selectDay(date) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selected day details
            selectedDay?.let { dayData ->
                DayDetailCard(dayData)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayNum: Int,
    dayData: CalendarDayData?,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasData = dayData?.hasData == true
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderMod = if (isToday) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .then(borderMod)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(2.dp)
        ) {
            Text(
                text = dayNum.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            )
            if (hasData) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    if (dayData!!.sleepEntries.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SleepButtonColor)
                        )
                    }
                    if (dayData.diaperEntries.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PeeColor)
                        )
                    }
                    if (dayData.feedEntries.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(FeedColor)
                        )
                    }
                    if (dayData.activityEntries.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(StrollerColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetailCard(dayData: CalendarDayData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = dayData.date.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!dayData.hasData) {
                Text(
                    text = "No entries",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            if (dayData.sleepEntries.isNotEmpty()) {
                Text(
                    text = "Sleep (${DateTimeUtil.formatDuration(dayData.totalSleep)})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                dayData.sleepEntries.forEach { entry ->
                    val endText = entry.endTime?.toString() ?: "ongoing"
                    Text(
                        text = "  ${entry.startTime} - $endText (${DateTimeUtil.formatDuration(entry.duration)})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (dayData.feedEntries.isNotEmpty()) {
                Text(
                    text = "Feeding (${DateTimeUtil.formatDuration(dayData.totalFeed)})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                dayData.feedEntries.forEach { entry ->
                    val endText = entry.endTime?.toString() ?: "ongoing"
                    Text(
                        text = "  ${entry.startTime} - $endText ${entry.side.label} (${DateTimeUtil.formatDuration(entry.duration)})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (dayData.diaperEntries.isNotEmpty()) {
                val pee = dayData.diaperEntries.count { it.type == DiaperType.PEE }
                val poo = dayData.diaperEntries.count { it.type == DiaperType.POO }
                val both = dayData.diaperEntries.count { it.type == DiaperType.PEEPOO }
                Text(
                    text = "Diapers (${dayData.totalDiapers}): $pee pee, $poo poo, $both both",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                dayData.diaperEntries.forEach { entry ->
                    Text(
                        text = "  ${entry.time} - ${entry.type.label}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (dayData.activityEntries.isNotEmpty()) {
                Text(
                    text = "Activities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                dayData.activityEntries.forEach { entry ->
                    val noteText = if (entry.note != null) " - ${entry.note}" else ""
                    Text(
                        text = "  ${entry.time} - ${entry.type.label}$noteText",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
