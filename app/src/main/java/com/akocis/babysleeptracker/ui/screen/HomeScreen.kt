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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.TrackingState
import com.akocis.babysleeptracker.ui.component.BigActionButton
import com.akocis.babysleeptracker.ui.component.StatusBanner
import com.akocis.babysleeptracker.ui.theme.PeeColor
import com.akocis.babysleeptracker.ui.theme.PeePooColor
import com.akocis.babysleeptracker.ui.theme.PooColor
import com.akocis.babysleeptracker.ui.theme.SleepButtonColor
import com.akocis.babysleeptracker.ui.theme.StopButtonColor
import com.akocis.babysleeptracker.util.DateTimeUtil
import com.akocis.babysleeptracker.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToManualEntry: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()
    val elapsedTime by viewModel.elapsedTime.collectAsStateWithLifecycle()
    val todayStats by viewModel.todayStats.collectAsStateWithLifecycle()
    val hasFile by viewModel.hasFile.collectAsStateWithLifecycle()
    val undoLabel by viewModel.undoLabel.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Baby Sleep Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onNavigateToManualEntry) {
                        Icon(Icons.Default.Add, "New Entry", tint = MaterialTheme.colorScheme.onPrimary)
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
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) {
                undoLabel?.let { label ->
                    Snackbar(
                        action = {
                            TextButton(onClick = { viewModel.undoLastAction() }) {
                                Text("Undo")
                            }
                        },
                        dismissAction = {
                            TextButton(onClick = { viewModel.dismissUndo() }) {
                                Text("Dismiss")
                            }
                        }
                    ) {
                        Text("Logged: $label")
                    }
                }
            }
        }
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
                    text = "Create Data File",
                    containerColor = MaterialTheme.colorScheme.primary,
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
                isSleeping = trackingState is TrackingState.Sleeping,
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

            Spacer(modifier = Modifier.height(16.dp))

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
                        Text("Pee: ${stats.peeCount}  Poo: ${stats.pooCount}  Both: ${stats.peepooCount}")
                        Text("Total diapers: ${stats.totalDiapers}")
                    }
                }
            }
        }
    }
}
