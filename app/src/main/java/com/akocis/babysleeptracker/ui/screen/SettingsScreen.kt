package com.akocis.babysleeptracker.ui.screen

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import com.akocis.babysleeptracker.model.BabySex
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.GeoLocation
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.repository.WeatherRepository
import com.akocis.babysleeptracker.ui.component.TimePickerDialog
import com.akocis.babysleeptracker.util.AlarmScheduler
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
    onNavigateToSync: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var themeMode by remember { mutableStateOf(prefsRepository.themeMode) }
    var babyName by remember { mutableStateOf(prefsRepository.babyName ?: "") }
    var babyBirthDate by remember { mutableStateOf(prefsRepository.babyBirthDate) }
    var babySex by remember { mutableStateOf(prefsRepository.babySex?.let { BabySex.fromString(it) } ?: BabySex.BOY) }
    var useKg by remember { mutableStateOf(prefsRepository.useKg) }
    var useCm by remember { mutableStateOf(prefsRepository.useCm) }
    var bottleUseOz by remember { mutableStateOf(prefsRepository.bottleUseOz) }
    var useCelsius by remember { mutableStateOf(prefsRepository.useCelsius) }
    var useHpa by remember { mutableStateOf(prefsRepository.useHpa) }

    var telemetryEnabled by remember { mutableStateOf(prefsRepository.telemetryEnabled) }

    // Location / weather
    var locationName by remember { mutableStateOf(prefsRepository.locationName ?: "") }
    var locationSearch by remember { mutableStateOf("") }
    var locationResults by remember { mutableStateOf<List<GeoLocation>>(emptyList()) }
    var isSearchingLocation by remember { mutableStateOf(false) }
    val weatherRepository = remember { WeatherRepository(context) }

    // Auto-save baby info to file whenever name, birth date, or sex change
    fun autoSaveBabyInfo() {
        val uri = prefsRepository.fileUri ?: return
        val name = babyName.takeIf { it.isNotBlank() } ?: return
        val bd = babyBirthDate ?: return
        prefsRepository.babyName = name
        prefsRepository.babySex = babySex.name
        scope.launch {
            try {
                fileRepository.saveBabyInfo(uri, name, bd, babySex)
            } catch (_: Exception) {}
        }
    }
    var showBirthDatePicker by remember { mutableStateOf(false) }
    var dayStartHour by remember { mutableStateOf(prefsRepository.dayStartHour) }
    var dayEndHour by remember { mutableStateOf(prefsRepository.dayEndHour) }
    var showDayStartPicker by remember { mutableStateOf(false) }
    var showDayEndPicker by remember { mutableStateOf(false) }

    // Alarm settings
    var sleepAlarmEnabled by remember { mutableStateOf(prefsRepository.sleepAlarmEnabled) }
    var sleepAlarmMinutes by remember { mutableStateOf(prefsRepository.sleepAlarmMinutes) }
    var sleepAlarmRingtoneUri by remember { mutableStateOf(prefsRepository.sleepAlarmRingtone) }
    var feedAlarmEnabled by remember { mutableStateOf(prefsRepository.feedAlarmEnabled) }
    var feedAlarmMinutes by remember { mutableStateOf(prefsRepository.feedAlarmMinutes) }
    var feedAlarmRingtoneUri by remember { mutableStateOf(prefsRepository.feedAlarmRingtone) }
    var breastAlarmEnabled by remember { mutableStateOf(prefsRepository.breastAlarmEnabled) }
    var breastAlarmMinutes by remember { mutableStateOf(prefsRepository.breastAlarmMinutes) }
    var breastAlarmRingtoneUri by remember { mutableStateOf(prefsRepository.breastAlarmRingtone) }

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

    fun getRingtoneUri(data: Intent?): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
    }

    val sleepRingtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = getRingtoneUri(result.data)
            sleepAlarmRingtoneUri = uri?.toString()
            prefsRepository.sleepAlarmRingtone = uri?.toString()
        }
    }

    val feedRingtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = getRingtoneUri(result.data)
            feedAlarmRingtoneUri = uri?.toString()
            prefsRepository.feedAlarmRingtone = uri?.toString()
        }
    }

    val breastRingtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = getRingtoneUri(result.data)
            breastAlarmRingtoneUri = uri?.toString()
            prefsRepository.breastAlarmRingtone = uri?.toString()
        }
    }

    fun ringtonePickerIntent(currentUri: String?, title: String): Intent {
        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            if (currentUri != null) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
            } else {
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                )
            }
        }
    }

    fun getRingtoneTitle(uriStr: String?): String {
        if (uriStr == null) return "Default alarm"
        return try {
            val ringtone = RingtoneManager.getRingtone(context, Uri.parse(uriStr))
            ringtone?.getTitle(context) ?: "Default alarm"
        } catch (_: Exception) {
            "Default alarm"
        }
    }

    fun formatAlarmDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
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
                            autoSaveBabyInfo()
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
                        onValueChange = {
                            babyName = it
                            prefsRepository.babyName = it
                            autoSaveBabyInfo()
                        },
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val sexOptions = listOf(BabySex.BOY to "Boy", BabySex.GIRL to "Girl")
                        sexOptions.forEachIndexed { index, (sex, label) ->
                            SegmentedButton(
                                selected = babySex == sex,
                                onClick = {
                                    babySex = sex
                                    prefsRepository.babySex = sex.name
                                    autoSaveBabyInfo()
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = sexOptions.size)
                            ) {
                                Text(label)
                            }
                        }
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
                    val fileDisplayName = remember(prefsRepository.fileUri) {
                        prefsRepository.fileUri?.let { uri ->
                            try {
                                context.contentResolver.query(
                                    uri,
                                    arrayOf(OpenableColumns.DISPLAY_NAME),
                                    null, null, null
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) cursor.getString(0) else null
                                }
                            } catch (_: Exception) {
                                null
                            }
                        } ?: "Not set"
                    }
                    Text(
                        text = fileDisplayName,
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

            // Sync
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sync",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (prefsRepository.isDropboxConfigured)
                            "Connected to Dropbox"
                        else
                            "Sync data across devices via Dropbox",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onNavigateToSync,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (prefsRepository.isDropboxConfigured) "Sync Settings"
                            else "Set Up Sync"
                        )
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
                            text = "Switches to dark theme between ${String.format("%02d:00", dayEndHour)} and ${String.format("%02d:00", dayStartHour)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Units
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Units",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Weight")
                        SingleChoiceSegmentedButtonRow {
                            listOf(true to "kg", false to "lbs").forEachIndexed { index, (kg, label) ->
                                SegmentedButton(
                                    selected = useKg == kg,
                                    onClick = { useKg = kg; prefsRepository.useKg = kg },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                                ) { Text(label) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Length")
                        SingleChoiceSegmentedButtonRow {
                            listOf(true to "cm", false to "in").forEachIndexed { index, (cm, label) ->
                                SegmentedButton(
                                    selected = useCm == cm,
                                    onClick = { useCm = cm; prefsRepository.useCm = cm },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                                ) { Text(label) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Volume")
                        SingleChoiceSegmentedButtonRow {
                            listOf(false to "ml", true to "oz").forEachIndexed { index, (oz, label) ->
                                SegmentedButton(
                                    selected = bottleUseOz == oz,
                                    onClick = { bottleUseOz = oz; prefsRepository.bottleUseOz = oz },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                                ) { Text(label) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Temperature")
                        SingleChoiceSegmentedButtonRow {
                            listOf(true to "\u00B0C", false to "\u00B0F").forEachIndexed { index, (celsius, label) ->
                                SegmentedButton(
                                    selected = useCelsius == celsius,
                                    onClick = { useCelsius = celsius; prefsRepository.useCelsius = celsius },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                                ) { Text(label) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pressure")
                        SingleChoiceSegmentedButtonRow {
                            listOf(true to "hPa", false to "inHg").forEachIndexed { index, (hpa, label) ->
                                SegmentedButton(
                                    selected = useHpa == hpa,
                                    onClick = { useHpa = hpa; prefsRepository.useHpa = hpa },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                                ) { Text(label) }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location (Weather)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Location",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "For weather data in calendar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    if (locationName.isNotBlank()) {
                        Text(
                            text = locationName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = locationSearch,
                        onValueChange = { query ->
                            locationSearch = query
                            if (query.length >= 2) {
                                isSearchingLocation = true
                                scope.launch {
                                    locationResults = weatherRepository.searchLocations(query)
                                    isSearchingLocation = false
                                }
                            } else {
                                locationResults = emptyList()
                            }
                        },
                        label = { Text("Search city") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (isSearchingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    )
                    if (locationResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        locationResults.forEach { loc ->
                            Text(
                                text = loc.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        prefsRepository.locationLat = loc.latitude
                                        prefsRepository.locationLon = loc.longitude
                                        prefsRepository.locationName = loc.displayName
                                        locationName = loc.displayName
                                        locationSearch = ""
                                        locationResults = emptyList()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                    if (locationName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                prefsRepository.locationLat = null
                                prefsRepository.locationLon = null
                                prefsRepository.locationName = null
                                locationName = ""
                            }
                        ) {
                            Text("Clear Location")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Telemetry
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Telemetry",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Show noise level, temperature, humidity and pressure from phone sensors on home screen. Requires microphone permission for noise level. Uses extra battery.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(
                            checked = telemetryEnabled,
                            onCheckedChange = {
                                telemetryEnabled = it
                                prefsRepository.telemetryEnabled = it
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Day / Night Hours
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Day / Night Hours",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Used for day/night stats and auto theme",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    OutlinedButton(
                        onClick = { showDayStartPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Day starts at ${String.format("%02d:00", dayStartHour)}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showDayEndPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Night starts at ${String.format("%02d:00", dayEndHour)}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Alarms
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Alarms",
                        style = MaterialTheme.typography.titleLarge
                    )

                    // Sleep alarm
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Sleep alarm")
                        Switch(
                            checked = sleepAlarmEnabled,
                            onCheckedChange = {
                                if (it && !AlarmScheduler.canScheduleExactAlarms(context)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Grant \"Alarms & reminders\" permission to enable alarms")
                                    }
                                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                    return@Switch
                                }
                                sleepAlarmEnabled = it
                                prefsRepository.sleepAlarmEnabled = it
                            }
                        )
                    }
                    if (sleepAlarmEnabled) {
                        Text(
                            text = "Alert when baby sleeps longer than ${formatAlarmDuration(sleepAlarmMinutes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = sleepAlarmMinutes.toFloat(),
                            onValueChange = {
                                sleepAlarmMinutes = (Math.round(it / 15f) * 15).coerceIn(15, 480)
                            },
                            onValueChangeFinished = {
                                prefsRepository.sleepAlarmMinutes = sleepAlarmMinutes
                            },
                            valueRange = 15f..480f,
                            steps = (480 - 15) / 15 - 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                sleepRingtoneLauncher.launch(
                                    ringtonePickerIntent(sleepAlarmRingtoneUri, "Sleep Alarm Sound")
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sound: ${getRingtoneTitle(sleepAlarmRingtoneUri)}")
                        }
                    }

                    // Feed alarm
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Feeding alarm")
                        Switch(
                            checked = feedAlarmEnabled,
                            onCheckedChange = {
                                if (it && !AlarmScheduler.canScheduleExactAlarms(context)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Grant \"Alarms & reminders\" permission to enable alarms")
                                    }
                                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                    return@Switch
                                }
                                feedAlarmEnabled = it
                                prefsRepository.feedAlarmEnabled = it
                            }
                        )
                    }
                    if (feedAlarmEnabled) {
                        Text(
                            text = "Alert when baby hasn't been fed for ${formatAlarmDuration(feedAlarmMinutes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = feedAlarmMinutes.toFloat(),
                            onValueChange = {
                                feedAlarmMinutes = (Math.round(it / 15f) * 15).coerceIn(15, 480)
                            },
                            onValueChangeFinished = {
                                prefsRepository.feedAlarmMinutes = feedAlarmMinutes
                            },
                            valueRange = 15f..480f,
                            steps = (480 - 15) / 15 - 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                feedRingtoneLauncher.launch(
                                    ringtonePickerIntent(feedAlarmRingtoneUri, "Feeding Alarm Sound")
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sound: ${getRingtoneTitle(feedAlarmRingtoneUri)}")
                        }
                    }

                    // Breast feed alarm
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Breast feed alarm")
                        Switch(
                            checked = breastAlarmEnabled,
                            onCheckedChange = {
                                if (it && !AlarmScheduler.canScheduleExactAlarms(context)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Grant \"Alarms & reminders\" permission to enable alarms")
                                    }
                                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                    return@Switch
                                }
                                breastAlarmEnabled = it
                                prefsRepository.breastAlarmEnabled = it
                            }
                        )
                    }
                    if (breastAlarmEnabled) {
                        Text(
                            text = "Alert when baby hasn't been breast fed for ${formatAlarmDuration(breastAlarmMinutes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = breastAlarmMinutes.toFloat(),
                            onValueChange = {
                                breastAlarmMinutes = (Math.round(it / 15f) * 15).coerceIn(15, 480)
                            },
                            onValueChangeFinished = {
                                prefsRepository.breastAlarmMinutes = breastAlarmMinutes
                            },
                            valueRange = 15f..480f,
                            steps = (480 - 15) / 15 - 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                breastRingtoneLauncher.launch(
                                    ringtonePickerIntent(breastAlarmRingtoneUri, "Breast Feed Alarm Sound")
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sound: ${getRingtoneTitle(breastAlarmRingtoneUri)}")
                        }
                    }
                }
            }

            if (showDayStartPicker) {
                TimePickerDialog(
                    title = "Day starts at",
                    initialTime = java.time.LocalTime.of(dayStartHour, 0),
                    onConfirm = { time ->
                        dayStartHour = time.hour
                        prefsRepository.dayStartHour = time.hour
                        if (themeMode == "auto") onThemeModeChanged("auto")
                        showDayStartPicker = false
                    },
                    onDismiss = { showDayStartPicker = false }
                )
            }

            if (showDayEndPicker) {
                TimePickerDialog(
                    title = "Night starts at",
                    initialTime = java.time.LocalTime.of(dayEndHour, 0),
                    onConfirm = { time ->
                        dayEndHour = time.hour
                        prefsRepository.dayEndHour = time.hour
                        if (themeMode == "auto") onThemeModeChanged("auto")
                        showDayEndPicker = false
                    },
                    onDismiss = { showDayEndPicker = false }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
