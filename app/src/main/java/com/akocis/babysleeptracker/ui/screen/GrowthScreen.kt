package com.akocis.babysleeptracker.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akocis.babysleeptracker.data.WhoGrowthData
import com.akocis.babysleeptracker.model.MeasurementEntry
import com.akocis.babysleeptracker.ui.component.GrowthChart
import com.akocis.babysleeptracker.ui.component.MeasurementPoint
import com.akocis.babysleeptracker.ui.component.TimePickerDialog
import com.akocis.babysleeptracker.util.DateTimeUtil
import com.akocis.babysleeptracker.viewmodel.GrowthViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
    var editingMeasurement by remember { mutableStateOf<MeasurementEntry?>(null) }
    var fullscreenChart by remember { mutableStateOf<FullscreenChartData?>(null) }

    if (showAddDialog) {
        MeasurementDialog(
            useMetric = useMetric,
            onDismiss = { showAddDialog = false },
            onConfirm = { entry ->
                viewModel.addMeasurement(entry)
                showAddDialog = false
            }
        )
    }

    editingMeasurement?.let { entry ->
        MeasurementDialog(
            useMetric = useMetric,
            existing = entry,
            onDismiss = { editingMeasurement = null },
            onConfirm = { updated ->
                viewModel.updateMeasurement(entry, updated)
                editingMeasurement = null
            },
            onDelete = {
                viewModel.deleteMeasurement(entry)
                editingMeasurement = null
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
                                    .clickable { editingMeasurement = m }
                                    .padding(vertical = 4.dp),
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementDialog(
    useMetric: Boolean,
    existing: MeasurementEntry? = null,
    onDismiss: () -> Unit,
    onConfirm: (MeasurementEntry) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isEdit = existing != null
    var isMetric by remember { mutableStateOf(useMetric) }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var time by remember { mutableStateOf(existing?.time ?: java.time.LocalTime.now().withSecond(0).withNano(0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Initialize text fields: convert from metric storage to display units
    var weightText by remember {
        mutableStateOf(
            existing?.weightKg?.let {
                if (useMetric) "%.2f".format(it) else "%.1f".format(it * 2.20462)
            } ?: ""
        )
    }
    var heightText by remember {
        mutableStateOf(
            existing?.heightCm?.let {
                if (useMetric) "%.1f".format(it) else "%.1f".format(it / 2.54)
            } ?: ""
        )
    }
    var headText by remember {
        mutableStateOf(
            existing?.headCm?.let {
                if (useMetric) "%.1f".format(it) else "%.1f".format(it / 2.54)
            } ?: ""
        )
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Measurement") },
            text = { Text("Delete measurement from ${date}?") },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
        return
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
        return
    }

    if (showTimePicker) {
        TimePickerDialog(
            title = "Time",
            initialTime = time,
            onConfirm = {
                time = it
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isEdit) "Edit Measurement" else "Add Measurement")
                if (isEdit && onDelete != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            Column {
                // Date selector
                OutlinedTextField(
                    value = date.toString(),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Time selector
                OutlinedTextField(
                    value = time.format(DateTimeUtil.TIME_FORMAT),
                    onValueChange = {},
                    label = { Text("Time") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true },
                    enabled = false,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(true to "Metric", false to "Imperial")
                    options.forEachIndexed { index, (metric, label) ->
                        SegmentedButton(
                            selected = isMetric == metric,
                            onClick = {
                                if (isMetric != metric) {
                                    // Convert current values to new unit system
                                    weightText = convertFieldValue(weightText, isMetric, metric, isWeight = true)
                                    heightText = convertFieldValue(heightText, isMetric, metric, isWeight = false)
                                    headText = convertFieldValue(headText, isMetric, metric, isWeight = false)
                                    isMetric = metric
                                }
                            },
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
                    onConfirm(MeasurementEntry(date, weightKg, heightCm, headCm, existing?.id, time))
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun convertFieldValue(text: String, fromMetric: Boolean, toMetric: Boolean, isWeight: Boolean): String {
    val value = text.toDoubleOrNull() ?: return text
    return if (isWeight) {
        if (fromMetric && !toMetric) "%.1f".format(value * 2.20462)  // kg -> lbs
        else if (!fromMetric && toMetric) "%.2f".format(value / 2.20462) // lbs -> kg
        else text
    } else {
        if (fromMetric && !toMetric) "%.1f".format(value / 2.54)  // cm -> in
        else if (!fromMetric && toMetric) "%.1f".format(value * 2.54) // in -> cm
        else text
    }
}
