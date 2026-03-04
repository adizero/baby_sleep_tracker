package com.akocis.babysleeptracker.ui.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.util.DateTimeUtil
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefsRepository: PreferencesRepository,
    fileRepository: FileRepository,
    onThemeModeChanged: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var themeMode by remember { mutableStateOf(prefsRepository.themeMode) }
    var babyName by remember { mutableStateOf(prefsRepository.babyName ?: "") }
    var babyBirthDate by remember { mutableStateOf(prefsRepository.babyBirthDate) }
    var showBirthDatePicker by remember { mutableStateOf(false) }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefsRepository.fileUri = uri
                scope.launch {
                    snackbarHostState.showSnackbar("File location updated")
                }
            }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { sourceUri ->
                val targetUri = prefsRepository.fileUri ?: return@let
                scope.launch {
                    val count = fileRepository.importFrom(targetUri, sourceUri)
                    snackbarHostState.showSnackbar("Imported $count entries")
                }
            }
        }
    }

    if (showBirthDatePicker) {
        val initialMillis = babyBirthDate?.atStartOfDay(ZoneId.of("UTC"))
            ?.toInstant()?.toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showBirthDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            babyBirthDate = selected
                            prefsRepository.babyBirthDate = selected
                            // Save to file too
                            val uri = prefsRepository.fileUri
                            if (uri != null && babyName.isNotBlank()) {
                                scope.launch {
                                    try {
                                        fileRepository.saveBabyInfo(uri, babyName, selected)
                                    } catch (_: Exception) {
                                        // File write may fail
                                    }
                                }
                            }
                        }
                        showBirthDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showBirthDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Baby info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Baby Info",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = babyName,
                        onValueChange = { babyName = it },
                        label = { Text("Baby Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showBirthDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (babyBirthDate != null)
                                "Birth Date: ${babyBirthDate!!.format(DateTimeUtil.DATE_FORMAT)}"
                            else
                                "Set Birth Date"
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (babyName.isNotBlank()) {
                                prefsRepository.babyName = babyName
                                val uri = prefsRepository.fileUri
                                val bd = babyBirthDate
                                if (uri != null && bd != null) {
                                    scope.launch {
                                        try {
                                            fileRepository.saveBabyInfo(uri, babyName, bd)
                                            snackbarHostState.showSnackbar("Baby info saved")
                                        } catch (_: Exception) {
                                            snackbarHostState.showSnackbar("Failed to save to file")
                                        }
                                    }
                                } else if (bd == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Please set a birth date first")
                                    }
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Name saved to preferences")
                                    }
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please enter a name")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Baby Info")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File location
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data File",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = prefsRepository.fileUri?.lastPathSegment ?: "Not set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "text/plain"
                            }
                            openFileLauncher.launch(intent)
                        }
                    ) {
                        Text("Change File")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Import
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Import",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Import entries from another text file",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "text/plain"
                            }
                            importFileLauncher.launch(intent)
                        },
                        enabled = prefsRepository.fileUri != null
                    ) {
                        Text("Import File")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Theme
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf("light" to "Light", "dark" to "Dark", "auto" to "Auto")
                        options.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = {
                                    themeMode = mode
                                    prefsRepository.themeMode = mode
                                    onThemeModeChanged(mode)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = options.size
                                )
                            ) {
                                Text(label)
                            }
                        }
                    }
                    if (themeMode == "auto") {
                        Text(
                            text = "Switches to dark theme between 7 PM and 7 AM",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
