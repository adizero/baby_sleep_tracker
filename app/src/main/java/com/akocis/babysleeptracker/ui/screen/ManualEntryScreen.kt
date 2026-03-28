package com.akocis.babysleeptracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akocis.babysleeptracker.ui.component.WheelPicker
import com.akocis.babysleeptracker.model.ActivityType
import com.akocis.babysleeptracker.model.BottleType
import com.akocis.babysleeptracker.model.DiaperType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.akocis.babysleeptracker.model.FeedSide
import com.akocis.babysleeptracker.model.NoiseType
import com.akocis.babysleeptracker.ui.component.BigActionButton
import com.akocis.babysleeptracker.ui.component.TimePickerDialog
import com.akocis.babysleeptracker.util.DateTimeUtil
import com.akocis.babysleeptracker.viewmodel.EntryKind
import com.akocis.babysleeptracker.viewmodel.ManualEntryViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    viewModel: ManualEntryViewModel,
    onBack: () -> Unit
) {
    val date by viewModel.date.collectAsStateWithLifecycle()
    val startTime by viewModel.startTime.collectAsStateWithLifecycle()
    val endTime by viewModel.endTime.collectAsStateWithLifecycle()
    val hasEndTime by viewModel.hasEndTime.collectAsStateWithLifecycle()
    val entryKind by viewModel.entryKind.collectAsStateWithLifecycle()
    val diaperType by viewModel.diaperType.collectAsStateWithLifecycle()
    val activityType by viewModel.activityType.collectAsStateWithLifecycle()
    val noteText by viewModel.noteText.collectAsStateWithLifecycle()
    val feedSide by viewModel.feedSide.collectAsStateWithLifecycle()
    val bottleType by viewModel.bottleType.collectAsStateWithLifecycle()
    val bottleAmountMl by viewModel.bottleAmountMl.collectAsStateWithLifecycle()
    val bottleUseOz by viewModel.bottleUseOz.collectAsStateWithLifecycle()
    val noiseType by viewModel.noiseType.collectAsStateWithLifecycle()
    val hcColors by viewModel.hcColors.collectAsStateWithLifecycle()
    val measureWeightText by viewModel.measureWeightText.collectAsStateWithLifecycle()
    val measureHeightText by viewModel.measureHeightText.collectAsStateWithLifecycle()
    val measureHeadText by viewModel.measureHeadText.collectAsStateWithLifecycle()
    val measureUseKg by viewModel.measureUseKg.collectAsStateWithLifecycle()
    val measureUseCm by viewModel.measureUseCm.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Long)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.resetSaved()
            onBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "Edit Entry" else "New Entry") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Entry type selector (two rows)
            val kindsRow1 = listOf(EntryKind.SLEEP to "Sleep", EntryKind.FEED to "Feed", EntryKind.BOTTLE to "Bottle", EntryKind.DIAPER to "Diaper")
            val kindsRow2 = listOf(EntryKind.ACTIVITY to "Activity", EntryKind.NOISE to "Noise", EntryKind.HC to "HC", EntryKind.MEASURE to "Growth")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                kindsRow1.forEachIndexed { index, (kind, label) ->
                    SegmentedButton(
                        selected = entryKind == kind,
                        onClick = { viewModel.setEntryKind(kind) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = kindsRow1.size)
                    ) {
                        Text(label, maxLines = 1, softWrap = false)
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                kindsRow2.forEachIndexed { index, (kind, label) ->
                    SegmentedButton(
                        selected = entryKind == kind,
                        onClick = { viewModel.setEntryKind(kind) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = kindsRow2.size)
                    ) {
                        Text(label, maxLines = 1, softWrap = false)
                    }
                }
            }

            // Date picker
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Date: ${date.format(DateTimeUtil.DATE_FORMAT)}")
            }

            // Start time / Time
            OutlinedButton(
                onClick = { showStartTimePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (entryKind == EntryKind.SLEEP || entryKind == EntryKind.FEED || entryKind == EntryKind.NOISE || entryKind == EntryKind.HC) "Start: ${startTime.format(DateTimeUtil.TIME_FORMAT)}"
                    else "Time: ${startTime.format(DateTimeUtil.TIME_FORMAT)}"
                )
            }
            // Start time +/- buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { viewModel.setStartTime(startTime.minusMinutes(10)) }) {
                    Text("-10m", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.setStartTime(startTime.minusMinutes(1)) },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text("-1m", fontSize = 12.sp)
                }
                Text(
                    text = startTime.format(DateTimeUtil.TIME_FORMAT),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                OutlinedButton(onClick = { viewModel.setStartTime(startTime.plusMinutes(1)) }) {
                    Text("+1m", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.setStartTime(startTime.plusMinutes(10)) },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text("+10m", fontSize = 12.sp)
                }
            }

            // Bottle type and amount (only for bottle entries)
            if (entryKind == EntryKind.BOTTLE) {
                Text("Type:", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BottleType.entries.forEach { type ->
                        FilterChip(
                            selected = bottleType == type,
                            onClick = { viewModel.setBottleType(type) },
                            label = { Text(type.label) }
                        )
                    }
                }
                Text("Amount:", style = MaterialTheme.typography.titleLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(false to "ml", true to "oz").forEachIndexed { index, (oz, label) ->
                        SegmentedButton(
                            selected = bottleUseOz == oz,
                            onClick = { viewModel.setBottleUseOz(oz) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                        ) { Text(label) }
                    }
                }
                val mlAmounts = (5..300 step 5).toList()
                val ozAmounts = (5..100 step 5).toList()
                if (bottleUseOz) {
                    val currentOzX10 = ozAmounts.minBy { kotlin.math.abs(it - ((bottleAmountMl / 29.5735) * 10).toInt()) }
                    WheelPicker(
                        items = ozAmounts,
                        initialValue = currentOzX10,
                        onValueChanged = { viewModel.setBottleAmountMl(((it / 10.0) * 29.5735).toInt()) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "oz",
                        formatItem = { "%.1f".format(it / 10.0) },
                        visibleItemCount = 3
                    )
                } else {
                    val snappedMl = mlAmounts.minBy { kotlin.math.abs(it - bottleAmountMl) }
                    WheelPicker(
                        items = mlAmounts,
                        initialValue = snappedMl,
                        onValueChanged = { viewModel.setBottleAmountMl(it) },
                        modifier = Modifier.fillMaxWidth(),
                        visibleItemCount = 3
                    )
                }
            }

            // Noise type (for noise entries)
            if (entryKind == EntryKind.NOISE) {
                Text("Type:", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NoiseType.entries.forEach { type ->
                        FilterChip(
                            selected = noiseType == type,
                            onClick = { viewModel.setNoiseType(type) },
                            label = { Text(type.label) }
                        )
                    }
                }
            }

            // HC colors (for HC entries)
            if (entryKind == EntryKind.HC) {
                OutlinedTextField(
                    value = hcColors,
                    onValueChange = { viewModel.setHcColors(it) },
                    label = { Text("Colors (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // End time (for sleep, feed, noise, and HC entries)
            if (entryKind == EntryKind.SLEEP || entryKind == EntryKind.FEED || entryKind == EntryKind.NOISE || entryKind == EntryKind.HC) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hasEndTime,
                        onCheckedChange = { viewModel.setHasEndTime(it) }
                    )
                    Text("Has end time")
                }
                if (hasEndTime) {
                    OutlinedButton(
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("End: ${endTime.format(DateTimeUtil.TIME_FORMAT)}")
                    }
                    // Duration display with +/- adjustment buttons
                    val duration = java.time.Duration.between(startTime, endTime)
                    val durationText = if (!duration.isNegative) DateTimeUtil.formatDuration(duration) else "—"
                    val smallStep = 1
                    val largeStep = 10
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { viewModel.adjustDuration(-largeStep) }) {
                            Text("-${largeStep}m", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.adjustDuration(-smallStep) },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text("-${smallStep}m", fontSize = 12.sp)
                        }
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        OutlinedButton(onClick = { viewModel.adjustDuration(smallStep) }) {
                            Text("+${smallStep}m", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.adjustDuration(largeStep) },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text("+${largeStep}m", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Feed side (only for feed entries)
            if (entryKind == EntryKind.FEED) {
                Text("Side:", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeedSide.entries.forEach { side ->
                        FilterChip(
                            selected = feedSide == side,
                            onClick = { viewModel.setFeedSide(side) },
                            label = { Text(side.label) }
                        )
                    }
                }
            }

            // Diaper type (only for diaper entries)
            if (entryKind == EntryKind.DIAPER) {
                Text("Type:", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiaperType.entries.forEach { type ->
                        FilterChip(
                            selected = diaperType == type,
                            onClick = { viewModel.setDiaperType(type) },
                            label = { Text(type.label) }
                        )
                    }
                }
            }

            // Activity type (only for activity entries)
            if (entryKind == EntryKind.ACTIVITY) {
                Text("Type:", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActivityType.entries.forEach { type ->
                        FilterChip(
                            selected = activityType == type,
                            onClick = { viewModel.setActivityType(type) },
                            label = { Text(type.label) }
                        )
                    }
                }

                // Note text field (for NOTE type)
                if (activityType == ActivityType.NOTE) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { viewModel.setNoteText(it) },
                        label = { Text("Note") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            }

            // Measurement fields (only for measure entries)
            if (entryKind == EntryKind.MEASURE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Weight:", style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow {
                        listOf(true to "kg", false to "lbs").forEachIndexed { index, (kg, label) ->
                            SegmentedButton(
                                selected = measureUseKg == kg,
                                onClick = { viewModel.setMeasureUseKg(kg) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                            ) { Text(label) }
                        }
                    }
                }
                OutlinedTextField(
                    value = measureWeightText,
                    onValueChange = { viewModel.setMeasureWeightText(it) },
                    label = { Text(if (measureUseKg) "Weight (kg)" else "Weight (lbs)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Length:", style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow {
                        listOf(true to "cm", false to "in").forEachIndexed { index, (cm, label) ->
                            SegmentedButton(
                                selected = measureUseCm == cm,
                                onClick = { viewModel.setMeasureUseCm(cm) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                            ) { Text(label) }
                        }
                    }
                }
                OutlinedTextField(
                    value = measureHeightText,
                    onValueChange = { viewModel.setMeasureHeightText(it) },
                    label = { Text(if (measureUseCm) "Length (cm)" else "Length (in)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = measureHeadText,
                    onValueChange = { viewModel.setMeasureHeadText(it) },
                    label = { Text(if (measureUseCm) "Head circ. (cm)" else "Head circ. (in)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            BigActionButton(
                text = if (viewModel.isEditMode) "Update" else "Save",
                containerColor = MaterialTheme.colorScheme.primary,
                onClick = { viewModel.save() }
            )
        }

        // Date picker dialog
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC"))
                    .toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val selected = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.of("UTC"))
                                    .toLocalDate()
                                viewModel.setDate(selected)
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
        }

        // Time picker dialogs
        if (showStartTimePicker) {
            TimePickerDialog(
                title = if (entryKind == EntryKind.SLEEP || entryKind == EntryKind.FEED || entryKind == EntryKind.NOISE || entryKind == EntryKind.HC) "Start Time" else "Time",
                initialTime = startTime,
                onConfirm = {
                    viewModel.setStartTime(it)
                    showStartTimePicker = false
                },
                onDismiss = { showStartTimePicker = false }
            )
        }

        if (showEndTimePicker) {
            TimePickerDialog(
                title = "End Time",
                initialTime = endTime,
                onConfirm = {
                    viewModel.setEndTime(it)
                    showEndTimePicker = false
                },
                onDismiss = { showEndTimePicker = false }
            )
        }
    }
}
