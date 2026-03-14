package com.akocis.babysleeptracker.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akocis.babysleeptracker.data.WhoGrowthData
import com.akocis.babysleeptracker.model.MeasurementEntry
import com.akocis.babysleeptracker.repository.EntryParser
import com.akocis.babysleeptracker.ui.component.GrowthChart
import com.akocis.babysleeptracker.ui.component.MeasurementPoint
import com.akocis.babysleeptracker.viewmodel.GrowthViewModel
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowthScreen(
    viewModel: GrowthViewModel,
    onBack: () -> Unit,
    onAddMeasurement: () -> Unit = {},
    onEditMeasurement: (String) -> Unit = {}
) {
    val measurements by viewModel.measurements.collectAsStateWithLifecycle()
    val babySex by viewModel.babySex.collectAsStateWithLifecycle()
    val babyBirthDate by viewModel.babyBirthDate.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val useMetric = viewModel.useMetric
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var fullscreenChart by remember { mutableStateOf<FullscreenChartData?>(null) }
    var deletingMeasurement by remember { mutableStateOf<MeasurementEntry?>(null) }

    deletingMeasurement?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingMeasurement = null },
            title = { Text("Delete Measurement") },
            text = { Text("Delete measurement from ${entry.date}?") },
            confirmButton = {
                TextButton(onClick = {
                    val rawLine = EntryParser.formatMeasurementEntry(entry)
                    viewModel.deleteMeasurement(entry)
                    deletingMeasurement = null
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Measurement deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.reAddMeasurement(rawLine)
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMeasurement = null }) { Text("Cancel") }
            }
        )
    }

    fullscreenChart?.let { data ->
        FullscreenChartDialog(
            data = data,
            onDismiss = { fullscreenChart = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Growth") },
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMeasurement) {
                Icon(Icons.Default.Add, "Add measurement")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.syncAndRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val birthDate = babyBirthDate
            val sex = babySex

            if (birthDate == null && !isLoading) {
                Text(
                    "Set baby's birth date in Settings to see growth charts.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Convert measurements to chart points
            fun toPoints(extract: (MeasurementEntry) -> Double?, convert: (Double) -> Double): List<MeasurementPoint> {
                if (birthDate == null) return emptyList()
                return measurements.mapNotNull { m ->
                    val value = extract(m) ?: return@mapNotNull null
                    val dayAge = ChronoUnit.DAYS.between(birthDate, m.date).toDouble()
                    val monthAge = dayAge / 30.4375
                    MeasurementPoint(monthAge, convert(value), m.date.toString())
                }
            }

            val weightConvert: (Double) -> Double = if (useMetric) { v -> v } else { v -> v * 2.20462 }
            val lengthConvert: (Double) -> Double = if (useMetric) { v -> v } else { v -> v / 2.54 }
            val weightUnit = if (useMetric) "kg" else "lbs"
            val lengthUnit = if (useMetric) "cm" else "in"

            // For percentile data, convert if imperial
            fun convertPercentiles(data: List<WhoGrowthData.PercentileRow>, convert: (Double) -> Double): List<WhoGrowthData.PercentileRow> {
                if (useMetric) return data
                return data.map { row ->
                    WhoGrowthData.PercentileRow(
                        row.monthAge,
                        convert(row.p3), convert(row.p15), convert(row.p50),
                        convert(row.p85), convert(row.p97)
                    )
                }
            }

            if (birthDate != null && sex != null) {
                val weightPercentiles = convertPercentiles(WhoGrowthData.getWeight(sex), weightConvert)
                val lengthPercentiles = convertPercentiles(WhoGrowthData.getLength(sex), lengthConvert)
                val headPercentiles = convertPercentiles(WhoGrowthData.getHead(sex), lengthConvert)
                val weightPoints = toPoints({ it.weightKg }, weightConvert)
                val lengthPoints = toPoints({ it.heightCm }, lengthConvert)
                val headPoints = toPoints({ it.headCm }, lengthConvert)

                // Weight chart
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    GrowthChart(
                        title = "Weight ($weightUnit)",
                        unit = weightUnit,
                        percentileData = weightPercentiles,
                        measurements = weightPoints,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp),
                        onDoubleClick = {
                            fullscreenChart = FullscreenChartData(
                                "Weight ($weightUnit)", weightUnit, weightPercentiles,
                                weightPoints, ChartType.WEIGHT
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Length chart
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    GrowthChart(
                        title = "Length ($lengthUnit)",
                        unit = lengthUnit,
                        percentileData = lengthPercentiles,
                        measurements = lengthPoints,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(12.dp),
                        onDoubleClick = {
                            fullscreenChart = FullscreenChartData(
                                "Length ($lengthUnit)", lengthUnit, lengthPercentiles,
                                lengthPoints, ChartType.LENGTH
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Head circumference chart
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    GrowthChart(
                        title = "Head Circumference ($lengthUnit)",
                        unit = lengthUnit,
                        percentileData = headPercentiles,
                        measurements = headPoints,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(12.dp),
                        onDoubleClick = {
                            fullscreenChart = FullscreenChartData(
                                "Head Circumference ($lengthUnit)", lengthUnit, headPercentiles,
                                headPoints, ChartType.HEAD
                            )
                        }
                    )
                }
            }

            // Measurements table
            if (measurements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Measurements",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Date", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("Weight", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("Length", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("Head", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f))
                            // Spacer for delete button column
                            Spacer(modifier = Modifier.weight(0.3f))
                        }
                        measurements.sortedByDescending { it.date }.forEach { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditMeasurement(EntryParser.formatMeasurementEntry(m)) }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(m.date.format(DateTimeFormatter.ofPattern("MM-dd")), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(
                                    m.weightKg?.let { formatValue(it, useMetric, isWeight = true) } ?: "-",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    m.heightCm?.let { formatValue(it, useMetric, isWeight = false) } ?: "-",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    m.headCm?.let { formatValue(it, useMetric, isWeight = false) } ?: "-",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(0.8f)
                                )
                                IconButton(
                                    onClick = { deletingMeasurement = m },
                                    modifier = Modifier.weight(0.3f)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
        }
    }
}

private fun formatValue(metricValue: Double, useMetric: Boolean, isWeight: Boolean): String {
    return if (useMetric) {
        if (isWeight) "${"%.3f".format(metricValue)} kg" else "${"%.1f".format(metricValue)} cm"
    } else {
        if (isWeight) "${"%.1f".format(metricValue * 2.20462)} lbs" else "${"%.1f".format(metricValue / 2.54)} in"
    }
}

private enum class ChartType { WEIGHT, LENGTH, HEAD }

private data class FullscreenChartData(
    val title: String,
    val unit: String,
    val percentileData: List<WhoGrowthData.PercentileRow>,
    val measurements: List<MeasurementPoint>,
    val chartType: ChartType
)

@Composable
private fun FullscreenChartDialog(
    data: FullscreenChartData,
    onDismiss: () -> Unit
) {
    val accentColor = when (data.chartType) {
        ChartType.WEIGHT -> MaterialTheme.colorScheme.primary
        ChartType.LENGTH -> MaterialTheme.colorScheme.tertiary
        ChartType.HEAD -> MaterialTheme.colorScheme.secondary
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                GrowthChart(
                    title = data.title,
                    unit = data.unit,
                    percentileData = data.percentileData,
                    measurements = data.measurements,
                    accentColor = accentColor,
                    isFullscreen = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp, top = 48.dp, bottom = 12.dp)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(Icons.Default.Close, "Close")
                }
            }
        }
    }
}

