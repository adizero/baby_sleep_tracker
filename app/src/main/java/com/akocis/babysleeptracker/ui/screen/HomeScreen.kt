package com.akocis.babysleeptracker.ui.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.akocis.babysleeptracker.ui.theme.WhiteNoiseColor
import com.akocis.babysleeptracker.util.DateTimeUtil
import com.akocis.babysleeptracker.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToManualEntry: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCalendar: () -> Unit = {}
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
    val noiseState by viewModel.noiseState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
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
            onConfirm = { ml ->
                viewModel.logBottleFeed(type, ml)
                pendingBottleType = null
            },
            onDismiss = { pendingBottleType = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                return@Scaffold
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

            Spacer(modifier = Modifier.height(24.dp))

            // Today's stats
            todayStats?.let { stats ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Sleep: ${DateTimeUtil.formatDuration(stats.totalSleep)} (${stats.sleepCount} naps)")
                        if (stats.feedCount > 0) {
                            Text("Feed: ${DateTimeUtil.formatDuration(stats.totalFeedDuration)} (${stats.feedCount} sessions)")
                        }
                        if (stats.totalBottleFeeds > 0) {
                            Text("Bottle: ${stats.totalBottleMl}ml (${stats.totalBottleFeeds} feeds)")
                        }
                        Text("Pee: ${stats.peeCount}  Poo: ${stats.pooCount}  Both: ${stats.peepooCount}")
                        Text("Total diapers: ${stats.totalDiapers}")
                        if (stats.totalActivities > 0) {
                            val parts = mutableListOf<String>()
                            if (stats.strollerCount > 0) parts.add("${stats.strollerCount} stroll")
                            if (stats.bathCount > 0) parts.add("${stats.bathCount} bath")
                            if (stats.noteCount > 0) parts.add("${stats.noteCount} note")
                            Text("Activities: ${parts.joinToString(", ")}")
                        }

                        // Time since last feed / bath
                        val sinceParts = mutableListOf<String>()
                        stats.timeSinceLastFeed?.let { sinceParts.add("Feed: $it ago") }
                        if (stats.timeSinceLastBreastFeed != null && stats.timeSinceLastBottleFeed != null) {
                            sinceParts.add("  Breast: ${stats.timeSinceLastBreastFeed} ago")
                            sinceParts.add("  Bottle: ${stats.timeSinceLastBottleFeed} ago")
                        }
                        stats.timeSinceLastBath?.let { sinceParts.add("Bath: $it ago") }
                        if (sinceParts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Last",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            sinceParts.forEach { Text(it) }
                        }
                    }
                }
            }
        }
    }
}
