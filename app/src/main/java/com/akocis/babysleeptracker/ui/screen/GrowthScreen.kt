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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akocis.babysleeptracker.data.WhoGrowthData
import com.akocis.babysleeptracker.model.BabySex
import com.akocis.babysleeptracker.model.MeasurementEntry
import com.akocis.babysleeptracker.ui.component.GrowthChart
import com.akocis.babysleeptracker.ui.component.MeasurementPoint
import com.akocis.babysleeptracker.viewmodel.GrowthViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowthScreen(
    viewModel: GrowthViewModel,
    onBack: () -> Unit
) {
    val measurements by viewModel.measurements.collectAsStateWithLifecycle()
    val babySex by viewModel.babySex.collectAsStateWithLifecycle()
    val babyBirthDate by viewModel.babyBirthDate.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val useMetric = viewModel.useMetric

    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddMeasurementDialog(
            useMetric = useMetric,
            onDismiss = { showAddDialog = false },
            onConfirm = { entry ->
                viewModel.addMeasurement(entry)
                showAddDialog = false
            }
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
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add measurement")
            }
        }
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
            if (babySex == null) {
                Text(
                    "Set baby's sex in Settings to see growth percentiles.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (babyBirthDate == null) {
                Text(
                    "Set baby's birth date in Settings to plot measurements on the chart.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            val birthDate = babyBirthDate
            val sex = babySex

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

            if (sex != null) {
                // Weight chart
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    GrowthChart(
                        title = "Weight ($weightUnit)",
                        unit = weightUnit,
                        percentileData = convertPercentiles(WhoGrowthData.getWeight(sex), weightConvert),
                        measurements = toPoints({ it.weightKg }, weightConvert),
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
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
                        percentileData = convertPercentiles(WhoGrowthData.getLength(sex), lengthConvert),
                        measurements = toPoints({ it.heightCm }, lengthConvert),
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(12.dp)
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
                        percentileData = convertPercentiles(WhoGrowthData.getHead(sex), lengthConvert),
                        measurements = toPoints({ it.headCm }, lengthConvert),
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(12.dp)
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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Date", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("Weight", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("Length", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("Head", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        }
                        measurements.sortedByDescending { it.date }.forEach { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(m.date.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
                                    modifier = Modifier.weight(1f)
                                )
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
        if (isWeight) "${"%.2f".format(metricValue)} kg" else "${"%.1f".format(metricValue)} cm"
    } else {
        if (isWeight) "${"%.1f".format(metricValue * 2.20462)} lbs" else "${"%.1f".format(metricValue / 2.54)} in"
    }
}

@Composable
private fun AddMeasurementDialog(
    useMetric: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (MeasurementEntry) -> Unit
) {
    var weightText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var headText by remember { mutableStateOf("") }
    var isMetric by remember { mutableStateOf(useMetric) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Measurement") },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(true to "Metric", false to "Imperial")
                    options.forEachIndexed { index, (metric, label) ->
                        @OptIn(ExperimentalMaterial3Api::class)
                        SegmentedButton(
                            selected = isMetric == metric,
                            onClick = { isMetric = metric },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                        ) {
                            Text(label)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text(if (isMetric) "Weight (kg)" else "Weight (lbs)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it },
                    label = { Text(if (isMetric) "Length (cm)" else "Length (in)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = headText,
                    onValueChange = { headText = it },
                    label = { Text(if (isMetric) "Head circ. (cm)" else "Head circ. (in)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = weightText.toDoubleOrNull()
                    val h = heightText.toDoubleOrNull()
                    val c = headText.toDoubleOrNull()
                    if (w == null && h == null && c == null) return@TextButton
                    // Convert to metric for storage
                    val weightKg = w?.let { if (isMetric) it else it / 2.20462 }
                    val heightCm = h?.let { if (isMetric) it else it * 2.54 }
                    val headCm = c?.let { if (isMetric) it else it * 2.54 }
                    onConfirm(MeasurementEntry(LocalDate.now(), weightKg, heightCm, headCm))
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
