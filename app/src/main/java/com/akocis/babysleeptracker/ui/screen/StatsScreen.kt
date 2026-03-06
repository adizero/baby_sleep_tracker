package com.akocis.babysleeptracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akocis.babysleeptracker.ui.component.DiaperChart
import com.akocis.babysleeptracker.ui.component.FeedChart
import com.akocis.babysleeptracker.ui.component.SleepChart
import com.akocis.babysleeptracker.ui.component.SleepPieChart
import com.akocis.babysleeptracker.util.DateTimeUtil
import com.akocis.babysleeptracker.viewmodel.StatsViewModel
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit
) {
    val dayStats by viewModel.dayStats.collectAsStateWithLifecycle()
    val daysBack by viewModel.daysBack.collectAsStateWithLifecycle()
    val summaryStats by viewModel.summaryStats.collectAsStateWithLifecycle()
    val movingAverage by viewModel.movingAverage.collectAsStateWithLifecycle()
    val feedMovingAverage by viewModel.feedMovingAverage.collectAsStateWithLifecycle()
    val is24hMode by viewModel.is24hMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
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
            // Range selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = daysBack == 0,
                    onClick = { viewModel.setDaysBack(0) },
                    label = { Text("24h") }
                )
                listOf(3, 7, 14, 30).forEach { days ->
                    FilterChip(
                        selected = daysBack == days,
                        onClick = { viewModel.setDaysBack(days) },
                        label = { Text("${days}d") }
                    )
                }
            }

            // Summary card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (is24hMode) "Last 24 Hours" else "Averages (${daysBack}d)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val suffix = if (is24hMode) "" else "/day"
                    Text("Sleep: ${DateTimeUtil.formatDuration(summaryStats.avgSleepPerDay)}$suffix")
                    Text("Naps: ${String.format("%.1f", summaryStats.avgNapsPerDay)}$suffix")
                    Text("Feed: ${DateTimeUtil.formatDuration(summaryStats.avgFeedPerDay)}$suffix")
                    Text("Feed sessions: ${String.format("%.1f", summaryStats.avgFeedSessionsPerDay)}$suffix")
                    if (summaryStats.avgDonorMlPerDay > 0f || summaryStats.avgFormulaMlPerDay > 0f) {
                        if (summaryStats.avgDonorMlPerDay > 0f) {
                            Text("Donor: ${String.format("%.0f", summaryStats.avgDonorMlPerDay)}ml (${String.format("%.1f", summaryStats.avgDonorCountPerDay)} feeds)$suffix")
                        }
                        if (summaryStats.avgFormulaMlPerDay > 0f) {
                            Text("Formula: ${String.format("%.0f", summaryStats.avgFormulaMlPerDay)}ml (${String.format("%.1f", summaryStats.avgFormulaCountPerDay)} feeds)$suffix")
                        }
                    }
                    Text("Diapers: ${String.format("%.1f", summaryStats.avgDiapersPerDay)}$suffix")
                    if (summaryStats.longestNap > Duration.ZERO) {
                        Text("Longest nap: ${DateTimeUtil.formatDuration(summaryStats.longestNap)}")
                    }
                    if (summaryStats.shortestNap > Duration.ZERO) {
                        Text("Shortest nap: ${DateTimeUtil.formatDuration(summaryStats.shortestNap)}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sleep chart with trend line
            Text(
                text = "Sleep Duration",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SleepChart(stats = dayStats, movingAverage = movingAverage)

            Spacer(modifier = Modifier.height(24.dp))

            // Feed duration chart
            Text(
                text = "Feed Duration",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeedChart(stats = dayStats, movingAverage = feedMovingAverage)

            Spacer(modifier = Modifier.height(24.dp))

            // Diaper chart
            Text(
                text = "Diaper Changes",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            DiaperChart(stats = dayStats)

            Spacer(modifier = Modifier.height(24.dp))

            if (!is24hMode) {
                // Day vs Night sleep pie chart
                val totalDaySleep = dayStats.fold(Duration.ZERO) { acc, s -> acc.plus(s.daySleep) }
                val totalNightSleep = dayStats.fold(Duration.ZERO) { acc, s -> acc.plus(s.nightSleep) }

                if (totalDaySleep > Duration.ZERO || totalNightSleep > Duration.ZERO) {
                    Text(
                        text = "Day vs Night Sleep",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SleepPieChart(daySleep = totalDaySleep, nightSleep = totalNightSleep)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Daily summaries
                Text(
                    text = "Daily Summary",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                dayStats.reversed().forEach { stats ->
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
                                text = stats.date.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Sleep: ${DateTimeUtil.formatDuration(stats.totalSleep)} (${stats.sleepCount} naps)")
                            if (stats.feedCount > 0) {
                                Text("Feed: ${DateTimeUtil.formatDuration(stats.totalFeedDuration)} (${stats.feedCount} sessions)")
                            }
                            if (stats.totalBottleFeeds > 0) {
                                Text("Bottle: ${stats.totalBottleMl}ml (${stats.totalBottleFeeds} feeds)")
                            }
                            if (stats.totalDiapers > 0) {
                                Text("Diapers: ${stats.peeCount} pee, ${stats.pooCount} poo, ${stats.peepooCount} both")
                            }
                            if (stats.totalActivities > 0) {
                                val parts = mutableListOf<String>()
                                if (stats.strollerCount > 0) parts.add("${stats.strollerCount} stroll")
                                if (stats.bathCount > 0) parts.add("${stats.bathCount} bath")
                                if (stats.noteCount > 0) parts.add("${stats.noteCount} note")
                                Text("Activities: ${parts.joinToString(", ")}")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
