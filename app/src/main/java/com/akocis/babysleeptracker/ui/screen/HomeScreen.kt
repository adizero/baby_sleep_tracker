package com.akocis.babysleeptracker.ui.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.os.Build
import com.akocis.babysleeptracker.model.ActivityType
import com.akocis.babysleeptracker.model.BottleType
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.FeedSide
import com.akocis.babysleeptracker.model.NoiseType
import com.akocis.babysleeptracker.model.TrackingState
import com.akocis.babysleeptracker.service.NoiseServiceState
import com.akocis.babysleeptracker.ui.component.BigActionButton
import com.akocis.babysleeptracker.ui.component.BottleAmountPickerDialog
import com.akocis.babysleeptracker.ui.component.StatusBanner
import com.akocis.babysleeptracker.ui.component.WhiteNoiseDialog
import com.akocis.babysleeptracker.ui.theme.BathColor
import com.akocis.babysleeptracker.ui.theme.DonorColor
import com.akocis.babysleeptracker.ui.theme.FeedColor
import com.akocis.babysleeptracker.ui.theme.FormulaColor
import com.akocis.babysleeptracker.ui.theme.PumpedColor
import com.akocis.babysleeptracker.ui.theme.NoteColor
import com.akocis.babysleeptracker.ui.theme.PeeColor
import com.akocis.babysleeptracker.ui.theme.PeePooColor
import com.akocis.babysleeptracker.ui.theme.PooColor
import com.akocis.babysleeptracker.ui.theme.SleepButtonColor
import com.akocis.babysleeptracker.ui.theme.StopButtonColor
import com.akocis.babysleeptracker.ui.theme.StrollerColor
import com.akocis.babysleeptracker.ui.theme.HighContrastColor
import com.akocis.babysleeptracker.ui.theme.WhiteNoiseColor
import com.akocis.babysleeptracker.util.DateTimeUtil
import com.akocis.babysleeptracker.repository.HourlyWeather
import com.akocis.babysleeptracker.repository.WeatherRepository
import com.akocis.babysleeptracker.viewmodel.HomeViewModel
import java.time.LocalDate
import java.time.LocalTime

internal fun formatVolume(ml: Int, useOz: Boolean): String {
    return if (useOz) "${"%.1f".format(ml / 29.5735)}oz" else "${ml}ml"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToManualEntry: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToGrowth: () -> Unit = {},
    onNavigateToHighContrast: () -> Unit = {},
    onNavigateToMilestones: () -> Unit = {}
) {
    val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()
    val elapsedTime by viewModel.elapsedTime.collectAsStateWithLifecycle()
    val todayStats by viewModel.todayStats.collectAsStateWithLifecycle()
    val hasFile by viewModel.hasFile.collectAsStateWithLifecycle()
    val undoLabel by viewModel.undoLabel.collectAsStateWithLifecycle()
    val babyName by viewModel.babyName.collectAsStateWithLifecycle()
    val babyAge by viewModel.babyAge.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val bottlePresetMl by viewModel.bottlePresetMl.collectAsStateWithLifecycle()
    val bottleUseOz by viewModel.bottleUseOz.collectAsStateWithLifecycle()
    val noiseState by viewModel.noiseState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val todayWeather by viewModel.todayWeather.collectAsStateWithLifecycle()
    val tomorrowWeather by viewModel.tomorrowWeather.collectAsStateWithLifecycle()
    val hourlyForecast by viewModel.hourlyForecast.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var weatherExpanded by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var pendingBottleType by remember { mutableStateOf<BottleType?>(null) }
    var showNoiseDialog by remember { mutableStateOf(false) }

    // Request notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied, proceed either way */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Long)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(undoLabel) {
        undoLabel?.let { label ->
            val result = snackbarHostState.showSnackbar(
                message = "Logged: $label",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastAction()
            } else {
                viewModel.dismissUndo()
            }
        }
    }

    val createFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.onFileSelected(uri)
            }
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.onFileSelected(uri)
            }
        }
    }

    // Note dialog
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Add Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note text") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            viewModel.logActivity(ActivityType.NOTE, noteText)
                        }
                        noteText = ""
                        showNoteDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    noteText = ""
                    showNoteDialog = false
                }) { Text("Cancel") }
            }
        )
    }

    // White noise dialog
    if (showNoiseDialog) {
        WhiteNoiseDialog(
            initialNoiseType = NoiseType.fromString(viewModel.getLastNoiseType()) ?: NoiseType.WHITE,
            initialVolume = viewModel.getNoiseVolume(),
            initialFadeIn = viewModel.getNoiseFadeIn(),
            initialFadeOut = viewModel.getNoiseFadeOut(),
            onStart = { settings ->
                viewModel.startNoise(settings)
                showNoiseDialog = false
            },
            onDismiss = { showNoiseDialog = false }
        )
    }

    // Bottle amount dialog — shown for all bottle types
    pendingBottleType?.let { type ->
        BottleAmountPickerDialog(
            title = "${type.label} Amount",
            initialAmount = bottlePresetMl,
            useOz = bottleUseOz,
            onConfirm = { ml, useOz ->
                viewModel.logBottleFeed(type, ml, useOz)
                pendingBottleType = null
            },
            onDismiss = { pendingBottleType = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable(
                            enabled = babyAge != null,
                            onClick = onNavigateToMilestones
                        )
                    ) {
                        val title = if (babyName != null) "$babyName" else "Baby Sleep Tracker"
                        Text(title)
                        if (babyAge != null) {
                            Text(
                                text = "Age: $babyAge",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onNavigateToManualEntry) {
                        Icon(Icons.Default.Add, "New Entry", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onNavigateToCalendar) {
                        Icon(Icons.Default.CalendarMonth, "Calendar", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onNavigateToGrowth) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, "Growth", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onNavigateToStats) {
                        Icon(Icons.Default.BarChart, "Stats", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, "History", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasFile) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Choose a file to store data",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp)
                )
                BigActionButton(
                    text = "Open Existing File",
                    containerColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "text/plain"
                        }
                        openFileLauncher.launch(intent)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                BigActionButton(
                    text = "Create New File",
                    containerColor = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TITLE, "baby_sleep.txt")
                        }
                        createFileLauncher.launch(intent)
                    }
                )
                return@Column
            }

            // Status banner
            StatusBanner(
                trackingState = trackingState,
                elapsedTime = elapsedTime
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sleep toggle button
            val isSleeping = trackingState is TrackingState.Sleeping
            BigActionButton(
                text = if (isSleeping) "Stop Sleep" else "Start Sleep",
                containerColor = if (isSleeping) StopButtonColor else SleepButtonColor,
                onClick = { viewModel.toggleSleep() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Feeding buttons
            val isFeeding = trackingState is TrackingState.Feeding
            val feedingLeft = trackingState is TrackingState.Feeding && (trackingState as TrackingState.Feeding).side == FeedSide.LEFT
            val feedingRight = trackingState is TrackingState.Feeding && (trackingState as TrackingState.Feeding).side == FeedSide.RIGHT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BigActionButton(
                    text = "Feed L",
                    containerColor = if (feedingLeft) StopButtonColor else FeedColor,
                    onClick = { viewModel.startFeeding(FeedSide.LEFT) },
                    modifier = Modifier.weight(1f)
                )
                BigActionButton(
                    text = "Stop",
                    containerColor = if (isFeeding) StopButtonColor else FeedColor.copy(alpha = 0.4f),
                    onClick = { viewModel.stopFeeding() },
                    modifier = Modifier.weight(1f),
                    enabled = isFeeding
                )
                BigActionButton(
                    text = "Feed R",
                    containerColor = if (feedingRight) StopButtonColor else FeedColor,
                    onClick = { viewModel.startFeeding(FeedSide.RIGHT) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottle feeding buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BigActionButton(
                    text = "Donor",
                    containerColor = DonorColor,
                    onClick = { pendingBottleType = BottleType.DONOR },
                    modifier = Modifier.weight(1f)
                )
                BigActionButton(
                    text = "Pumped",
                    containerColor = PumpedColor,
                    onClick = { pendingBottleType = BottleType.PUMPED },
                    modifier = Modifier.weight(1f)
                )
                BigActionButton(
                    text = "Formula",
                    containerColor = FormulaColor,
                    onClick = { pendingBottleType = BottleType.FORMULA },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Diaper buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BigActionButton(
                    text = "Pee",
                    containerColor = PeeColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { viewModel.logDiaper(DiaperType.PEE) },
                    modifier = Modifier.weight(1f)
                )
                BigActionButton(
                    text = "Poo",
                    containerColor = PooColor,
                    onClick = { viewModel.logDiaper(DiaperType.POO) },
                    modifier = Modifier.weight(1f)
                )
                BigActionButton(
                    text = "Both",
                    containerColor = PeePooColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { viewModel.logDiaper(DiaperType.PEEPOO) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Activity buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BigActionButton(
                    text = "Stroll",
                    containerColor = StrollerColor,
                    onClick = { viewModel.logActivity(ActivityType.STROLLER) },
                    modifier = Modifier.weight(1f)
                )
                BigActionButton(
                    text = "Bath",
                    containerColor = BathColor,
                    onClick = { viewModel.logActivity(ActivityType.BATH) },
                    modifier = Modifier.weight(1f)
                )
                BigActionButton(
                    text = "Note",
                    containerColor = NoteColor,
                    onClick = { showNoteDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // White noise button
            val isNoisePlaying = noiseState is NoiseServiceState.Playing
            val noiseElapsed = if (isNoisePlaying) {
                val playing = noiseState as NoiseServiceState.Playing
                var elapsed by remember { mutableStateOf(DateTimeUtil.formatElapsed(playing.startDate, playing.startTime)) }
                LaunchedEffect(playing) {
                    while (true) {
                        elapsed = DateTimeUtil.formatElapsed(playing.startDate, playing.startTime)
                        kotlinx.coroutines.delay(10_000)
                    }
                }
                elapsed
            } else null
            val noiseButtonText = if (isNoisePlaying) {
                val playingType = (noiseState as NoiseServiceState.Playing).noiseType
                "Stop ${playingType.label} Noise $noiseElapsed"
            } else {
                "White Noise"
            }
            BigActionButton(
                text = noiseButtonText,
                containerColor = if (isNoisePlaying) StopButtonColor else WhiteNoiseColor,
                onClick = {
                    if (isNoisePlaying) {
                        viewModel.stopNoise()
                    } else {
                        showNoiseDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // High contrast images button
            BigActionButton(
                text = "High Contrast",
                containerColor = HighContrastColor,
                onClick = onNavigateToHighContrast
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Weather card
            todayWeather?.let { weather ->
                val today = LocalDate.now()
                val tomorrow = today.plusDays(1)
                val currentHour = LocalTime.now().hour
                val todayHours = hourlyForecast.filter { it.date == today && it.hour >= currentHour }
                val tomorrowHours = hourlyForecast.filter { it.date == tomorrow }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { weatherExpanded = !weatherExpanded },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Weather",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "${WeatherRepository.weatherIcon(weather.weatherCode)} ${"%.0f".format(weather.maxTemp)}\u00B0",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text(
                            text = WeatherRepository.weatherDescription(weather.weatherCode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )

                        if (weatherExpanded) {
                            // Today's hourly forecast
                            if (todayHours.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                WeatherHourlyRow(todayHours)
                            }

                            // Tomorrow's forecast
                            if (tomorrowHours.isNotEmpty() || tomorrowWeather != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Tomorrow",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    tomorrowWeather?.let { tw ->
                                        Text(
                                            text = "${WeatherRepository.weatherIcon(tw.weatherCode)} ${"%.0f".format(tw.maxTemp)}\u00B0 ${WeatherRepository.weatherDescription(tw.weatherCode)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                                if (tomorrowHours.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    WeatherHourlyRow(tomorrowHours)
                                }
                            }
                        } else if (todayHours.isNotEmpty()) {
                            // Collapsed: show compact hourly preview
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val previewHours = if (todayHours.size > 6) {
                                todayHours.filterIndexed { i, _ -> i % 2 == 0 || i == todayHours.lastIndex }
                            } else {
                                todayHours
                            }
                            WeatherHourlyRow(previewHours)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            todayStats?.let { stats ->
                // --- Last card (shown first) ---
                val hasLastData = stats.timeSinceLastSleep != null ||
                    stats.timeSinceLastFeed != null ||
                    stats.timeSinceLastDiaper != null ||
                    stats.timeSinceLastBath != null ||
                    stats.lastWeightText != null ||
                    stats.lastHeightText != null ||
                    stats.lastHeadText != null
                if (hasLastData) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Last",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Sleep row with Nap/Slumber sub-rows
                            stats.timeSinceLastSleep?.let {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Sleep", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(
                                        "$it ago",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                stats.timeSinceLastNap?.let { nap ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Nap",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            "$nap ago",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                stats.timeSinceLastSlumber?.let { slumber ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Slumber",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            "$slumber ago",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                            // Feed row
                            stats.timeSinceLastFeed?.let {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Feed", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(
                                        "$it ago",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            if (stats.timeSinceLastBreastFeed != null && stats.timeSinceLastBottleFeed != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Breast",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        "${stats.timeSinceLastBreastFeed} ago",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Bottle",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        "${stats.timeSinceLastBottleFeed} ago",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            // Diaper row with Pee/Poo sub-rows
                            stats.timeSinceLastDiaper?.let {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Diaper", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(
                                        "$it ago",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                stats.timeSinceLastPee?.let { pee ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Pee",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            "$pee ago",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                stats.timeSinceLastPoo?.let { poo ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Poo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            "$poo ago",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                            // Bath row
                            stats.timeSinceLastBath?.let {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Bath", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(
                                        "$it ago",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            // Measurement rows
                            if (stats.lastWeightText != null || stats.lastHeightText != null || stats.lastHeadText != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Measure",
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    stats.timeSinceLastMeasure?.let {
                                        Text(
                                            "$it ago",
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                stats.lastWeightText?.let { wt ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Weight",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            wt,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                stats.lastHeightText?.let { ht ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Height",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            ht,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                stats.lastHeadText?.let { hdt ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Head",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            hdt,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // --- Today card ---
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sleep")
                            Text(
                                "${DateTimeUtil.formatDuration(stats.totalSleep)} (${stats.sleepCount} naps)",
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (stats.feedCount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Feed")
                                Text(
                                    "${DateTimeUtil.formatDuration(stats.totalFeedDuration)} (${stats.feedCount})",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (stats.leftFeedCount > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Left", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${DateTimeUtil.formatDuration(stats.leftFeedDuration)} (${stats.leftFeedCount})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (stats.rightFeedCount > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Right", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${DateTimeUtil.formatDuration(stats.rightFeedDuration)} (${stats.rightFeedCount})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        if (stats.totalBottleFeeds > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Bottle")
                                Text(
                                    "${formatVolume(stats.totalBottleMl, bottleUseOz)} (${stats.totalBottleFeeds})",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (stats.donorCount > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Donor", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${formatVolume(stats.donorMl, bottleUseOz)} (${stats.donorCount})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (stats.formulaCount > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Formula", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${formatVolume(stats.formulaMl, bottleUseOz)} (${stats.formulaCount})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (stats.pumpedCount > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Pumped", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${formatVolume(stats.pumpedMl, bottleUseOz)} (${stats.pumpedCount})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        // Pee row (pee + peepoo)
                        val peeTotal = stats.peeCount + stats.peepooCount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pee")
                            Text("$peeTotal", fontWeight = FontWeight.Medium)
                        }
                        // Poo row (poo + peepoo)
                        val pooTotal = stats.pooCount + stats.peepooCount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Poo")
                            Text("$pooTotal", fontWeight = FontWeight.Medium)
                        }
                        if (stats.totalActivities > 0) {
                            val parts = mutableListOf<String>()
                            if (stats.strollerCount > 0) parts.add("${stats.strollerCount} stroll")
                            if (stats.bathCount > 0) parts.add("${stats.bathCount} bath")
                            if (stats.noteCount > 0) parts.add("${stats.noteCount} note")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Activities")
                                Text(parts.joinToString(", "), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}

@Composable
private fun WeatherHourlyRow(hours: List<HourlyWeather>) {
    // Show every 3rd hour if too many, to keep it readable
    val display = if (hours.size > 8) {
        hours.filterIndexed { i, _ -> i % 3 == 0 || i == hours.lastIndex }
    } else if (hours.size > 6) {
        hours.filterIndexed { i, _ -> i % 2 == 0 || i == hours.lastIndex }
    } else {
        hours
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (h in display) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%d:00".format(h.hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                )
                Text(
                    text = WeatherRepository.weatherIcon(h.weatherCode),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${"%.0f".format(h.temp)}\u00B0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
