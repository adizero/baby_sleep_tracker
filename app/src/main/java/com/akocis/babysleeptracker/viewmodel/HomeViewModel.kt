package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.akocis.babysleeptracker.data.WhoGrowthData
import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.ActivityType
import com.akocis.babysleeptracker.model.BabySex
import com.akocis.babysleeptracker.model.BottleFeedEntry
import com.akocis.babysleeptracker.model.BottleType
import com.akocis.babysleeptracker.model.DayStats
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.FeedEntry
import com.akocis.babysleeptracker.model.FeedSide
import com.akocis.babysleeptracker.model.HighContrastEntry
import com.akocis.babysleeptracker.model.NoiseType
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.model.TrackingState
import com.akocis.babysleeptracker.model.WhiteNoiseEntry
import com.akocis.babysleeptracker.repository.EntryParser
import com.akocis.babysleeptracker.repository.DayWeather
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.HourlyWeather
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.repository.SyncHelper
import com.akocis.babysleeptracker.repository.WeatherRepository
import com.akocis.babysleeptracker.service.TelemetryData
import com.akocis.babysleeptracker.service.TelemetryManager
import com.akocis.babysleeptracker.service.NoiseServiceState
import com.akocis.babysleeptracker.service.WhiteNoiseService
import com.akocis.babysleeptracker.ui.component.NoiseSettings
import com.akocis.babysleeptracker.util.AlarmScheduler
import com.akocis.babysleeptracker.util.DateTimeUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val prefsRepository = PreferencesRepository(application)
    private val weatherRepository = WeatherRepository(application)
    private val telemetryManager = TelemetryManager(application)

    private val _trackingState = MutableStateFlow<TrackingState>(TrackingState.Idle)
    val trackingState: StateFlow<TrackingState> = _trackingState

    private val _elapsedTime = MutableStateFlow("")
    val elapsedTime: StateFlow<String> = _elapsedTime

    private val _todayStats = MutableStateFlow<DayStats?>(null)
    val todayStats: StateFlow<DayStats?> = _todayStats

    private val _hasFile = MutableStateFlow(false)
    val hasFile: StateFlow<Boolean> = _hasFile

    private val _undoLabel = MutableStateFlow<String?>(null)
    val undoLabel: StateFlow<String?> = _undoLabel

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _babyName = MutableStateFlow<String?>(null)
    val babyName: StateFlow<String?> = _babyName

    private val _babyAge = MutableStateFlow<String?>(null)
    val babyAge: StateFlow<String?> = _babyAge

    private val _bottlePresetMl = MutableStateFlow(42)
    val bottlePresetMl: StateFlow<Int> = _bottlePresetMl
    private val _bottleUseOz = MutableStateFlow(false)
    val bottleUseOz: StateFlow<Boolean> = _bottleUseOz

    val noiseState: StateFlow<NoiseServiceState> = WhiteNoiseService.serviceState

    private val _todayWeather = MutableStateFlow<DayWeather?>(null)
    val todayWeather: StateFlow<DayWeather?> = _todayWeather

    private val _tomorrowWeather = MutableStateFlow<DayWeather?>(null)
    val tomorrowWeather: StateFlow<DayWeather?> = _tomorrowWeather

    private val _hourlyForecast = MutableStateFlow<List<HourlyWeather>>(emptyList())
    val hourlyForecast: StateFlow<List<HourlyWeather>> = _hourlyForecast

    private val _useCelsius = MutableStateFlow(true)
    val useCelsius: StateFlow<Boolean> = _useCelsius

    private val _useHpa = MutableStateFlow(true)
    val useHpa: StateFlow<Boolean> = _useHpa

    private val _telemetryEnabled = MutableStateFlow(false)
    val telemetryEnabled: StateFlow<Boolean> = _telemetryEnabled

    val telemetryData: StateFlow<TelemetryData> = telemetryManager.data

    private var telemetryJob: Job? = null

    private var weatherLoadedDate: LocalDate? = null
    private var weatherRefreshJob: Job? = null

    private var timerJob: Job? = null
    private var undoDismissJob: Job? = null
    private var noiseObserverJob: Job? = null
    private var pendingWrite = false
    private var lastSleepEndDate: LocalDate? = null
    private var lastSleepEndTime: LocalTime? = null
    private var pendingNoiseEntry: WhiteNoiseEntry? = null
    private var pendingHcEntry: HighContrastEntry? = null

    init {
        _trackingState.value = prefsRepository.loadTrackingState()
        _hasFile.value = prefsRepository.fileUri != null
        when (_trackingState.value) {
            is TrackingState.Sleeping, is TrackingState.Feeding -> startTimer()
            is TrackingState.Idle -> {
                // Awake timer will start after syncAndRefresh populates lastSleepEnd
            }
        }
        loadBabyInfo()
        loadBottlePreset()
        _useCelsius.value = prefsRepository.useCelsius
        _useHpa.value = prefsRepository.useHpa
        loadWeather()
        startTelemetryIfEnabled()
        syncAndRefresh(showIndicator = false)
        closeStaleOngoingEntries()
        observeNoiseState()
        observeSyncCompleted()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    private fun loadBabyInfo() {
        _babyName.value = prefsRepository.babyName
        val birthDate = prefsRepository.babyBirthDate
        if (birthDate != null) {
            _babyAge.value = formatAge(birthDate)
        }
    }

    private fun loadWeather() {
        val lat = prefsRepository.locationLat ?: return
        val lon = prefsRepository.locationLon ?: return
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        weatherLoadedDate = today
        viewModelScope.launch {
            try {
                val forecast = weatherRepository.getForecast(lat, lon, today, tomorrow)
                _todayWeather.value = forecast[today]
                _tomorrowWeather.value = forecast[tomorrow]
                val hourly = weatherRepository.getHourlyForecast(lat, lon)
                _hourlyForecast.value = hourly
            } catch (_: Exception) { }
        }
        scheduleWeatherRefresh()
    }

    private fun scheduleWeatherRefresh() {
        weatherRefreshJob?.cancel()
        weatherRefreshJob = viewModelScope.launch {
            while (true) {
                delay(3_600_000L) // 1 hour
                loadWeather()
            }
        }
    }

    fun refreshSettingsState() {
        // Refresh units
        _useCelsius.value = prefsRepository.useCelsius
        _useHpa.value = prefsRepository.useHpa
        // Refresh weather if location changed or date changed
        val hasLocation = prefsRepository.hasLocation
        val hadWeather = _todayWeather.value != null
        val dateChanged = weatherLoadedDate != null && weatherLoadedDate != LocalDate.now()
        if (hasLocation && (!hadWeather || dateChanged)) {
            loadWeather()
        } else if (!hasLocation && hadWeather) {
            _todayWeather.value = null
            _tomorrowWeather.value = null
            _hourlyForecast.value = emptyList()
        }
        // Refresh telemetry
        refreshTelemetryState()
        // Refresh baby info
        loadBabyInfo()
    }

    fun refreshTelemetryState() {
        val enabled = prefsRepository.telemetryEnabled
        val wasEnabled = _telemetryEnabled.value
        _telemetryEnabled.value = enabled
        if (enabled && !wasEnabled) {
            startTelemetry()
        } else if (!enabled && wasEnabled) {
            stopTelemetry()
        }
    }

    fun startTelemetryIfEnabled() {
        _telemetryEnabled.value = prefsRepository.telemetryEnabled
        if (prefsRepository.telemetryEnabled) {
            startTelemetry()
        }
    }

    private fun startTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            telemetryManager.start()
        }
    }

    fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
        telemetryManager.stop()
    }

    private fun formatAge(birthDate: LocalDate): String {
        val today = LocalDate.now()
        val months = ChronoUnit.MONTHS.between(birthDate, today)
        val days = ChronoUnit.DAYS.between(birthDate.plusMonths(months), today)
        return when {
            months >= 12 -> {
                val years = months / 12
                val remainingMonths = months % 12
                if (remainingMonths > 0) "${years}y ${remainingMonths}m"
                else "${years}y"
            }
            months > 0 -> {
                if (days > 0) "${months}m ${days}d"
                else "${months}m"
            }
            else -> "${days}d"
        }
    }

    fun toggleSleep() {
        val uri = prefsRepository.fileUri ?: return
        when (val state = _trackingState.value) {
            is TrackingState.Idle -> {
                val now = TrackingState.Sleeping(LocalDate.now(), LocalTime.now().withSecond(0).withNano(0))
                _trackingState.value = now
                prefsRepository.saveTrackingState(now)
                pendingWrite = true
                startTimer()
                viewModelScope.launch {
                    try {
                        fileRepository.appendSleepEntry(uri, SleepEntry(now.startDate, now.startTime, null))
                        pendingWrite = false
                        scheduleSleepAlarmIfEnabled()
                        refreshTodayStats()
                        SyncHelper.notifyDataChanged()
                        showUndo("Sleep started at ${now.startTime}")
                    } catch (e: Exception) {
                        pendingWrite = false
                        _errorMessage.value = "Failed to save sleep entry: ${e.message}"
                    }
                }
            }
            is TrackingState.Sleeping -> {
                val endTime = LocalTime.now().withSecond(0).withNano(0)
                cancelSleepAlarm()
                viewModelScope.launch {
                    try {
                        val ongoingLine = "SLEEP ${state.startDate.format(DateTimeUtil.DATE_FORMAT)} ${state.startTime.format(DateTimeUtil.TIME_FORMAT)} -"
                        val completedLine = "SLEEP ${state.startDate.format(DateTimeUtil.DATE_FORMAT)} ${state.startTime.format(DateTimeUtil.TIME_FORMAT)} - ${endTime.format(DateTimeUtil.TIME_FORMAT)}"
                        fileRepository.updateEntry(uri, ongoingLine, completedLine)
                        _trackingState.value = TrackingState.Idle
                        prefsRepository.saveTrackingState(TrackingState.Idle)
                        lastSleepEndDate = LocalDate.now()
                        lastSleepEndTime = endTime
                        startAwakeTimer()
                        refreshTodayStats()
                        SyncHelper.notifyDataChanged()
                        showUndo("Sleep ${state.startTime} - $endTime")
                    } catch (e: Exception) {
                        _errorMessage.value = "Failed to save sleep entry: ${e.message}"
                    }
                }
            }
            is TrackingState.Feeding -> {
                // Auto-stop feeding, then start sleep
                viewModelScope.launch {
                    try {
                        finishFeeding(uri, state)
                        val now = TrackingState.Sleeping(LocalDate.now(), LocalTime.now().withSecond(0).withNano(0))
                        _trackingState.value = now
                        prefsRepository.saveTrackingState(now)
                        startTimer()
                        scheduleSleepAlarmIfEnabled()
                        fileRepository.appendSleepEntry(uri, SleepEntry(now.startDate, now.startTime, null))
                        refreshTodayStats()
                        SyncHelper.notifyDataChanged()
                        showUndo("Sleep started at ${now.startTime}")
                    } catch (e: Exception) {
                        _errorMessage.value = "Failed to save entry: ${e.message}"
                    }
                }
            }
        }
    }

    fun startFeeding(side: FeedSide) {
        val uri = prefsRepository.fileUri ?: return
        when (val state = _trackingState.value) {
            is TrackingState.Idle -> {
                val now = TrackingState.Feeding(side, LocalDate.now(), LocalTime.now().withSecond(0).withNano(0))
                _trackingState.value = now
                prefsRepository.saveTrackingState(now)
                pendingWrite = true
                cancelFeedAlarm()
                startTimer()
                viewModelScope.launch {
                    try {
                        fileRepository.appendFeedEntry(uri, FeedEntry(side, now.startDate, now.startTime))
                        pendingWrite = false
                        refreshTodayStats()
                        SyncHelper.notifyDataChanged()
                        showUndo("Feed (${side.label}) started at ${now.startTime}")
                    } catch (e: Exception) {
                        pendingWrite = false
                        _errorMessage.value = "Failed to save feed entry: ${e.message}"
                    }
                }
            }
            is TrackingState.Sleeping -> {
                // Auto-stop sleep, then start feeding
                cancelSleepAlarm()
                viewModelScope.launch {
                    try {
                        val endTime = LocalTime.now().withSecond(0).withNano(0)
                        val ongoingLine = "SLEEP ${state.startDate.format(DateTimeUtil.DATE_FORMAT)} ${state.startTime.format(DateTimeUtil.TIME_FORMAT)} -"
                        val completedLine = "SLEEP ${state.startDate.format(DateTimeUtil.DATE_FORMAT)} ${state.startTime.format(DateTimeUtil.TIME_FORMAT)} - ${endTime.format(DateTimeUtil.TIME_FORMAT)}"
                        fileRepository.updateEntry(uri, ongoingLine, completedLine)

                        val now = TrackingState.Feeding(side, LocalDate.now(), LocalTime.now().withSecond(0).withNano(0))
                        _trackingState.value = now
                        prefsRepository.saveTrackingState(now)
                        startTimer()
                        fileRepository.appendFeedEntry(uri, FeedEntry(side, now.startDate, now.startTime))
                        refreshTodayStats()
                        SyncHelper.notifyDataChanged()
                        showUndo("Feed (${side.label}) started at ${now.startTime}")
                    } catch (e: Exception) {
                        _errorMessage.value = "Failed to save entry: ${e.message}"
                    }
                }
            }
            is TrackingState.Feeding -> {
                if (state.side == side) {
                    // Same side — stop feeding
                    stopFeeding()
                } else {
                    // Switch sides — finish current, start new
                    viewModelScope.launch {
                        try {
                            finishFeeding(uri, state)
                            val now = TrackingState.Feeding(side, LocalDate.now(), LocalTime.now().withSecond(0).withNano(0))
                            _trackingState.value = now
                            prefsRepository.saveTrackingState(now)
                            startTimer()
                            fileRepository.appendFeedEntry(uri, FeedEntry(side, now.startDate, now.startTime))
                            refreshTodayStats()
                            SyncHelper.notifyDataChanged()
                            showUndo("Feed switched to ${side.label}")
                        } catch (e: Exception) {
                            _errorMessage.value = "Failed to save entry: ${e.message}"
                        }
                    }
                }
            }
        }
    }

    fun stopFeeding() {
        val uri = prefsRepository.fileUri ?: return
        val state = _trackingState.value
        if (state !is TrackingState.Feeding) return
        viewModelScope.launch {
            try {
                finishFeeding(uri, state)
                _trackingState.value = TrackingState.Idle
                prefsRepository.saveTrackingState(TrackingState.Idle)
                startAwakeTimer()
                scheduleFeedAlarmIfEnabled()
                refreshTodayStats()
                SyncHelper.notifyDataChanged()
                val endTime = LocalTime.now().withSecond(0).withNano(0)
                showUndo("Feed (${state.side.label}) ${state.startTime} - $endTime")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save feed entry: ${e.message}"
            }
        }
    }

    private suspend fun finishFeeding(uri: android.net.Uri, state: TrackingState.Feeding) {
        val endTime = LocalTime.now().withSecond(0).withNano(0)
        val tag = if (state.side == FeedSide.LEFT) "FEEDL" else "FEEDR"
        val ongoingLine = "$tag ${state.startDate.format(DateTimeUtil.DATE_FORMAT)} ${state.startTime.format(DateTimeUtil.TIME_FORMAT)} -"
        val completedLine = "$tag ${state.startDate.format(DateTimeUtil.DATE_FORMAT)} ${state.startTime.format(DateTimeUtil.TIME_FORMAT)} - ${endTime.format(DateTimeUtil.TIME_FORMAT)}"
        fileRepository.updateEntry(uri, ongoingLine, completedLine)
    }

    fun logDiaper(type: DiaperType) {
        val uri = prefsRepository.fileUri ?: return
        val now = LocalTime.now().withSecond(0).withNano(0)
        val entry = DiaperEntry(type, LocalDate.now(), now)
        viewModelScope.launch {
            try {
                fileRepository.appendDiaperEntry(uri, entry)
                refreshTodayStats()
                SyncHelper.notifyDataChanged()
                showUndo("${type.label} at $now")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save diaper entry: ${e.message}"
            }
        }
    }

    fun logBottleFeed(type: BottleType, amount: Int, useOz: Boolean = false) {
        val uri = prefsRepository.fileUri ?: return
        val now = LocalTime.now().withSecond(0).withNano(0)
        setBottlePreset(amount, useOz)
        val entry = BottleFeedEntry(type, LocalDate.now(), now, amount)
        val displayAmount = if (useOz) "${"%.1f".format(amount / 29.5735)}oz" else "${amount}ml"
        viewModelScope.launch {
            try {
                fileRepository.appendBottleFeedEntry(uri, entry)
                scheduleFeedAlarmIfEnabled()
                refreshTodayStats()
                SyncHelper.notifyDataChanged()
                showUndo("${type.label} $displayAmount at $now")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save bottle feed: ${e.message}"
            }
        }
    }

    fun setBottlePreset(ml: Int, useOz: Boolean) {
        _bottlePresetMl.value = ml
        prefsRepository.bottlePresetMl = ml
        _bottleUseOz.value = useOz
    }

    private fun loadBottlePreset() {
        val saved = prefsRepository.bottlePresetMl
        if (saved > 0) {
            _bottlePresetMl.value = saved
        }
        _bottleUseOz.value = prefsRepository.bottleUseOz
    }

    fun logActivity(type: ActivityType, note: String? = null) {
        val uri = prefsRepository.fileUri ?: return
        val now = LocalTime.now().withSecond(0).withNano(0)
        val entry = ActivityEntry(type, LocalDate.now(), now, note)
        viewModelScope.launch {
            try {
                fileRepository.appendActivityEntry(uri, entry)
                refreshTodayStats()
                SyncHelper.notifyDataChanged()
                showUndo("${type.label} at $now")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save activity: ${e.message}"
            }
        }
    }

    fun undoLastAction() {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            try {
                fileRepository.deleteLastLine(uri)
                _undoLabel.value = null
                undoDismissJob?.cancel()
                refreshTodayStats()
                SyncHelper.notifyDataChanged()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to undo: ${e.message}"
            }
        }
    }

    fun dismissUndo() {
        _undoLabel.value = null
        undoDismissJob?.cancel()
    }

    private fun showUndo(label: String) {
        _undoLabel.value = label
        undoDismissJob?.cancel()
        undoDismissJob = viewModelScope.launch {
            delay(5000)
            _undoLabel.value = null
        }
    }

    fun syncAndRefresh(showIndicator: Boolean = true) {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            if (showIndicator) _isRefreshing.value = true
            try {
                SyncHelper.pullLatest()
                refreshTodayStatsInternal(uri)
            } finally {
                if (showIndicator) _isRefreshing.value = false
            }
        }
    }

    fun refreshTodayStats() {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            refreshTodayStatsInternal(uri)
        }
    }

    private suspend fun refreshTodayStatsInternal(uri: Uri) {
        val data = fileRepository.readAll(uri)
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay()
        val tomorrowStart = today.plusDays(1).atStartOfDay()

        // Sleep/feed: split duration across day boundary (consider entries from yesterday too)
        val relevantSleep = data.sleepEntries.filter { it.date == today || it.date == today.minusDays(1) }
        val todaySleepDuration = relevantSleep.fold(Duration.ZERO) { acc, entry ->
            val entryStart = entry.date.atTime(entry.startTime)
            acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), todayStart, tomorrowStart))
        }

        val relevantFeeds = data.feedEntries.filter { it.date == today || it.date == today.minusDays(1) }
        val todayFeedDuration = relevantFeeds.fold(Duration.ZERO) { acc, entry ->
            val entryStart = entry.date.atTime(entry.startTime)
            acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), todayStart, tomorrowStart))
        }

        val relevantLeftFeeds = relevantFeeds.filter { it.side == FeedSide.LEFT }
        val todayLeftFeedDuration = relevantLeftFeeds.fold(Duration.ZERO) { acc, entry ->
            val entryStart = entry.date.atTime(entry.startTime)
            acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), todayStart, tomorrowStart))
        }
        val relevantRightFeeds = relevantFeeds.filter { it.side == FeedSide.RIGHT }
        val todayRightFeedDuration = relevantRightFeeds.fold(Duration.ZERO) { acc, entry ->
            val entryStart = entry.date.atTime(entry.startTime)
            acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), todayStart, tomorrowStart))
        }

        val todayDiapers = data.diaperEntries.filter { it.date == today }
        val todayBottle = data.bottleFeedEntries.filter { it.date == today }
        val todayActivities = data.activityEntries.filter { it.date == today }

        // Helper: compute actual end DateTime accounting for midnight crossing
        fun endDateTime(date: LocalDate, startTime: LocalTime, endTime: LocalTime): LocalDateTime {
            val endDate = if (endTime < startTime) date.plusDays(1) else date
            return endDate.atTime(endTime)
        }
        // Helper: epoch seconds of end time for sorting (adds 86400 if crossed midnight)
        fun endEpoch(date: LocalDate, startTime: LocalTime, endTime: LocalTime): Long {
            val extra = if (endTime < startTime) 86400L else 0L
            return date.toEpochDay() * 86400 + endTime.toSecondOfDay() + extra
        }

        // Find the most recent completed sleep entry (from all entries) for awake timer
        val lastCompletedSleep = data.sleepEntries
            .filter { !it.isOngoing }
            .maxByOrNull { endEpoch(it.date, it.startTime, it.endTime!!) }
        if (lastCompletedSleep?.endTime != null) {
            val endDt = endDateTime(lastCompletedSleep.date, lastCompletedSleep.startTime, lastCompletedSleep.endTime)
            lastSleepEndDate = endDt.toLocalDate()
            lastSleepEndTime = endDt.toLocalTime()
        }

        // Compute "time since" for feeds, bottle feeds, bath (across all entries)
        val now = LocalDateTime.now()

        // Last breast feed (skip ongoing, use end time of last completed)
        val lastBreastFeed = data.feedEntries
            .filter { !it.isOngoing }
            .maxByOrNull { endEpoch(it.date, it.startTime, it.endTime!!) }
        val timeSinceBreastFeed = lastBreastFeed?.let {
            val feedEnd = endDateTime(it.date, it.startTime, it.endTime!!)
            DateTimeUtil.formatDurationWithDays(Duration.between(feedEnd, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
        }

        // Last bottle feed
        val lastBottle = data.bottleFeedEntries
            .maxByOrNull { it.date.toEpochDay() * 86400 + it.time.toSecondOfDay() }
        val timeSinceBottleFeed = lastBottle?.let {
            val bottleTime = it.date.atTime(it.time)
            DateTimeUtil.formatDurationWithDays(Duration.between(bottleTime, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
        }

        // Last any feed (breast or bottle, time since end of last completed)
        val lastBreastEndEpoch = lastBreastFeed?.let {
            endEpoch(it.date, it.startTime, it.endTime!!)
        } ?: -1L
        val lastBottleEpoch = lastBottle?.let {
            it.date.toEpochDay() * 86400 + it.time.toSecondOfDay()
        } ?: -1L
        val timeSinceAnyFeed = if (lastBreastEndEpoch >= 0 || lastBottleEpoch >= 0) {
            if (lastBreastEndEpoch >= lastBottleEpoch) {
                val feedEnd = endDateTime(lastBreastFeed!!.date, lastBreastFeed.startTime, lastBreastFeed.endTime!!)
                DateTimeUtil.formatDurationWithDays(Duration.between(feedEnd, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
            } else {
                val bottleTime = lastBottle!!.date.atTime(lastBottle.time)
                DateTimeUtil.formatDurationWithDays(Duration.between(bottleTime, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
            }
        } else null

        // Last sleep (skip ongoing, use end time of last completed)
        val lastSleep = data.sleepEntries
            .filter { !it.isOngoing }
            .maxByOrNull { endEpoch(it.date, it.startTime, it.endTime!!) }
        val timeSinceSleep = lastSleep?.let {
            val sleepEnd = endDateTime(it.date, it.startTime, it.endTime!!)
            DateTimeUtil.formatDurationWithDays(Duration.between(sleepEnd, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
        }
        // Last nap (sleep <= 60m)
        val lastNap = data.sleepEntries
            .filter { !it.isOngoing && it.duration.toMinutes() <= 60 }
            .maxByOrNull { endEpoch(it.date, it.startTime, it.endTime!!) }
        val timeSinceNap = lastNap?.let {
            val napEnd = endDateTime(it.date, it.startTime, it.endTime!!)
            DateTimeUtil.formatDurationWithDays(Duration.between(napEnd, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
        }
        // Last slumber (sleep > 60m)
        val lastSlumber = data.sleepEntries
            .filter { !it.isOngoing && it.duration.toMinutes() > 60 }
            .maxByOrNull { endEpoch(it.date, it.startTime, it.endTime!!) }
        val timeSinceSlumber = lastSlumber?.let {
            val slumberEnd = endDateTime(it.date, it.startTime, it.endTime!!)
            DateTimeUtil.formatDurationWithDays(Duration.between(slumberEnd, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
        }

        // Last diaper (any type)
        val lastDiaper = data.diaperEntries
            .maxByOrNull { it.date.toEpochDay() * 86400 + it.time.toSecondOfDay() }
        val timeSinceDiaper = lastDiaper?.let {
            val diaperTime = it.date.atTime(it.time)
            DateTimeUtil.formatDurationWithDays(Duration.between(diaperTime, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
        }

        // Last pee (PEE or PEEPOO)
        val lastPee = data.diaperEntries
            .filter { it.type == DiaperType.PEE || it.type == DiaperType.PEEPOO }
            .maxByOrNull { it.date.toEpochDay() * 86400 + it.time.toSecondOfDay() }
        val timeSincePee = lastPee?.let {
            val peeTime = it.date.atTime(it.time)
            DateTimeUtil.formatDurationWithDays(Duration.between(peeTime, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
        }

        // Last poo (POO or PEEPOO)
        val lastPoo = data.diaperEntries
            .filter { it.type == DiaperType.POO || it.type == DiaperType.PEEPOO }
            .maxByOrNull { it.date.toEpochDay() * 86400 + it.time.toSecondOfDay() }
        val timeSincePoo = lastPoo?.let {
            val pooTime = it.date.atTime(it.time)
            DateTimeUtil.formatDurationWithDays(Duration.between(pooTime, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
        }

        // Last bath
        val lastBath = data.activityEntries
            .filter { it.type == ActivityType.BATH }
            .maxByOrNull { it.date.toEpochDay() * 86400 + it.time.toSecondOfDay() }
        val timeSinceBath = lastBath?.let {
            val bathTime = it.date.atTime(it.time)
            DateTimeUtil.formatDurationWithDays(Duration.between(bathTime, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
        }

        // Latest measurement with percentiles
        val useKg = prefsRepository.useKg
        val useCm = prefsRepository.useCm
        val birthDate = prefsRepository.babyBirthDate
        val babySex = prefsRepository.babySex?.let { BabySex.fromString(it) }
        val latestMeasurement = data.measurementEntries.maxByOrNull { it.date.toEpochDay() }

        fun calcPercentile(value: Double, percentileData: List<WhoGrowthData.PercentileRow>, monthAge: Double): String {
            val below = percentileData.lastOrNull { it.monthAge <= monthAge }
            val above = percentileData.firstOrNull { it.monthAge >= monthAge }
            if (below == null && above == null) return ""
            fun interp(getPct: (WhoGrowthData.PercentileRow) -> Double): Double {
                if (below == null) return getPct(above!!)
                if (above == null || below == above) return getPct(below)
                val t = (monthAge - below.monthAge) / (above.monthAge - below.monthAge)
                return getPct(below) + t * (getPct(above) - getPct(below))
            }
            val pcts = listOf(
                3 to interp { it.p3 }, 15 to interp { it.p15 }, 50 to interp { it.p50 },
                85 to interp { it.p85 }, 97 to interp { it.p97 }
            )
            return when {
                value <= pcts[0].second -> "<3rd"
                value >= pcts[4].second -> ">97th"
                else -> {
                    val lower = pcts.last { it.second <= value }
                    val upper = pcts.first { it.second >= value }
                    if (lower == upper) "${lower.first}th"
                    else {
                        val t = (value - lower.second) / (upper.second - lower.second)
                        "~${(lower.first + t * (upper.first - lower.first)).toInt()}th"
                    }
                }
            }
        }

        val lastWeightText = latestMeasurement?.weightKg?.let { w ->
            val valText = if (useKg) "${"%.3f".format(w)} kg" else "${"%.1f".format(w * 2.20462)} lbs"
            if (birthDate != null && babySex != null) {
                val monthAge = java.time.temporal.ChronoUnit.DAYS.between(birthDate, latestMeasurement.date) / 30.4375
                val pct = calcPercentile(w, WhoGrowthData.getWeight(babySex), monthAge)
                if (pct.isNotEmpty()) "$valText ($pct)" else valText
            } else valText
        }
        val lastHeightText = latestMeasurement?.heightCm?.let { h ->
            val valText = if (useCm) "${"%.1f".format(h)} cm" else "${"%.1f".format(h / 2.54)} in"
            if (birthDate != null && babySex != null) {
                val monthAge = java.time.temporal.ChronoUnit.DAYS.between(birthDate, latestMeasurement.date) / 30.4375
                val pct = calcPercentile(h, WhoGrowthData.getLength(babySex), monthAge)
                if (pct.isNotEmpty()) "$valText ($pct)" else valText
            } else valText
        }
        val lastHeadText = latestMeasurement?.headCm?.let { c ->
            val valText = if (useCm) "${"%.1f".format(c)} cm" else "${"%.1f".format(c / 2.54)} in"
            if (birthDate != null && babySex != null) {
                val monthAge = java.time.temporal.ChronoUnit.DAYS.between(birthDate, latestMeasurement.date) / 30.4375
                val pct = calcPercentile(c, WhoGrowthData.getHead(babySex), monthAge)
                if (pct.isNotEmpty()) "$valText ($pct)" else valText
            } else valText
        }
        val timeSinceMeasure = latestMeasurement?.let {
            val measureTime = it.date.atTime(it.time ?: java.time.LocalTime.NOON)
            DateTimeUtil.formatDurationWithDays(Duration.between(measureTime, now).let { d -> if (d.isNegative) d.plusHours(24) else d })
        }

        _todayStats.value = DayStats(
            date = today,
            totalSleep = todaySleepDuration,
            sleepCount = data.sleepEntries.count { it.date == today },
            peeCount = todayDiapers.count { it.type == DiaperType.PEE },
            pooCount = todayDiapers.count { it.type == DiaperType.POO },
            peepooCount = todayDiapers.count { it.type == DiaperType.PEEPOO },
            feedCount = data.feedEntries.count { it.date == today },
            totalFeedDuration = todayFeedDuration,
            leftFeedDuration = todayLeftFeedDuration,
            rightFeedDuration = todayRightFeedDuration,
            leftFeedCount = data.feedEntries.count { it.date == today && it.side == FeedSide.LEFT },
            rightFeedCount = data.feedEntries.count { it.date == today && it.side == FeedSide.RIGHT },
            donorCount = todayBottle.count { it.type == BottleType.DONOR },
            donorMl = todayBottle.filter { it.type == BottleType.DONOR }.sumOf { it.amountMl },
            formulaCount = todayBottle.count { it.type == BottleType.FORMULA },
            formulaMl = todayBottle.filter { it.type == BottleType.FORMULA }.sumOf { it.amountMl },
            pumpedCount = todayBottle.count { it.type == BottleType.PUMPED },
            pumpedMl = todayBottle.filter { it.type == BottleType.PUMPED }.sumOf { it.amountMl },
            strollerCount = todayActivities.count { it.type == ActivityType.STROLLER },
            bathCount = todayActivities.count { it.type == ActivityType.BATH },
            noteCount = todayActivities.count { it.type == ActivityType.NOTE },
            timeSinceLastFeed = timeSinceAnyFeed,
            timeSinceLastBreastFeed = timeSinceBreastFeed,
            timeSinceLastBottleFeed = timeSinceBottleFeed,
            timeSinceLastBath = timeSinceBath,
            timeSinceLastDiaper = timeSinceDiaper,
            timeSinceLastPee = timeSincePee,
            timeSinceLastPoo = timeSincePoo,
            timeSinceLastSleep = timeSinceSleep,
            timeSinceLastNap = timeSinceNap,
            timeSinceLastSlumber = timeSinceSlumber,
            lastWeightText = lastWeightText,
            lastHeightText = lastHeightText,
            lastHeadText = lastHeadText,
            timeSinceLastMeasure = timeSinceMeasure
        )

        // Auto-resolve bottle preset from history if unset
        if (prefsRepository.bottlePresetMl <= 0 && data.bottleFeedEntries.isNotEmpty()) {
            val lastAmount = data.bottleFeedEntries.last().amountMl
            _bottlePresetMl.value = lastAmount
            prefsRepository.bottlePresetMl = lastAmount
        }

        // Sync tracking state with file contents (skip if a write is in progress)
        if (!pendingWrite) {
            val ongoingFeed = data.feedEntries.find { it.isOngoing }
            val ongoingSleep = data.sleepEntries.find { it.isOngoing }
            when {
                ongoingFeed != null -> {
                    val state = TrackingState.Feeding(ongoingFeed.side, ongoingFeed.date, ongoingFeed.startTime)
                    if (_trackingState.value != state) {
                        _trackingState.value = state
                        prefsRepository.saveTrackingState(state)
                        startTimer()
                    }
                }
                ongoingSleep != null -> {
                    val state = TrackingState.Sleeping(ongoingSleep.date, ongoingSleep.startTime)
                    if (_trackingState.value != state) {
                        _trackingState.value = state
                        prefsRepository.saveTrackingState(state)
                        startTimer()
                    }
                }
                _trackingState.value !is TrackingState.Idle -> {
                    _trackingState.value = TrackingState.Idle
                    prefsRepository.saveTrackingState(TrackingState.Idle)
                    startAwakeTimer()
                }
                _trackingState.value is TrackingState.Idle && lastSleepEndDate != null -> {
                    startAwakeTimer()
                }
            }
        }

        // Schedule alarms based on current state
        when (_trackingState.value) {
            is TrackingState.Sleeping -> scheduleSleepAlarmIfEnabled()
            is TrackingState.Idle -> {
                // Schedule feed alarm from last feed end time
                val lastFeedEndDateTime = if (lastBreastEndEpoch >= lastBottleEpoch && lastBreastFeed != null) {
                    endDateTime(lastBreastFeed.date, lastBreastFeed.startTime, lastBreastFeed.endTime!!)
                } else if (lastBottle != null) {
                    lastBottle.date.atTime(lastBottle.time)
                } else null
                if (lastFeedEndDateTime != null) {
                    scheduleFeedAlarmFromLastFeed(lastFeedEndDateTime)
                }
            }
            is TrackingState.Feeding -> {} // No alarms while feeding
        }

        if (data.babyName != null) {
            _babyName.value = data.babyName
            prefsRepository.babyName = data.babyName
        }
        if (data.babyBirthDate != null) {
            _babyAge.value = formatAge(data.babyBirthDate)
            prefsRepository.babyBirthDate = data.babyBirthDate
        }
        if (data.babySex != null) {
            prefsRepository.babySex = data.babySex.name
        }
    }

    fun onFileSelected(uri: Uri) {
        prefsRepository.fileUri = uri
        _hasFile.value = true
        loadBabyInfo()
        refreshTodayStats()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                when (val state = _trackingState.value) {
                    is TrackingState.Sleeping ->
                        _elapsedTime.value = DateTimeUtil.formatElapsed(state.startDate, state.startTime)
                    is TrackingState.Feeding ->
                        _elapsedTime.value = DateTimeUtil.formatElapsed(state.startDate, state.startTime)
                    else -> {}
                }
                delay(10_000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _elapsedTime.value = ""
    }

    private fun startAwakeTimer() {
        timerJob?.cancel()
        val endDate = lastSleepEndDate
        val endTime = lastSleepEndTime
        if (endDate == null || endTime == null) {
            _elapsedTime.value = ""
            timerJob = null
            return
        }
        timerJob = viewModelScope.launch {
            while (true) {
                _elapsedTime.value = DateTimeUtil.formatElapsed(endDate, endTime)
                delay(10_000)
            }
        }
    }

    private fun scheduleSleepAlarmIfEnabled() {
        if (!prefsRepository.sleepAlarmEnabled) return
        val state = _trackingState.value as? TrackingState.Sleeping ?: return
        val ctx = getApplication<Application>()
        val startDateTime = state.startDate.atTime(state.startTime)
        val triggerMillis = startDateTime
            .plusMinutes(prefsRepository.sleepAlarmMinutes.toLong())
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        if (triggerMillis > System.currentTimeMillis()) {
            AlarmScheduler.scheduleSleepAlarm(ctx, triggerMillis, prefsRepository.sleepAlarmRingtone)
        }
    }

    private fun cancelSleepAlarm() {
        AlarmScheduler.cancelSleepAlarm(getApplication())
    }

    private fun scheduleFeedAlarmIfEnabled() {
        if (!prefsRepository.feedAlarmEnabled) return
        val ctx = getApplication<Application>()
        // Schedule from now (feed just ended or app just started idle)
        val triggerMillis = System.currentTimeMillis() + prefsRepository.feedAlarmMinutes.toLong() * 60_000
        AlarmScheduler.scheduleFeedAlarm(ctx, triggerMillis, prefsRepository.feedAlarmRingtone)
    }

    private fun scheduleFeedAlarmFromLastFeed(lastFeedDateTime: LocalDateTime) {
        if (!prefsRepository.feedAlarmEnabled) return
        val ctx = getApplication<Application>()
        val triggerMillis = lastFeedDateTime
            .plusMinutes(prefsRepository.feedAlarmMinutes.toLong())
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        if (triggerMillis > System.currentTimeMillis()) {
            AlarmScheduler.scheduleFeedAlarm(ctx, triggerMillis, prefsRepository.feedAlarmRingtone)
        }
    }

    private fun cancelFeedAlarm() {
        AlarmScheduler.cancelFeedAlarm(getApplication())
    }

    fun getLastNoiseType(): String = prefsRepository.lastNoiseType
    fun getNoiseVolume(): Float = prefsRepository.noiseVolume
    fun getNoiseFadeIn(): Int = prefsRepository.noiseFadeIn
    fun getNoiseFadeOut(): Int = prefsRepository.noiseFadeOut

    fun startHcEntry(colorsAbbrev: String) {
        val uri = prefsRepository.fileUri ?: return
        val now = LocalTime.now().withSecond(0).withNano(0)
        val id = EntryParser.generateId()
        val entry = HighContrastEntry(LocalDate.now(), now, null, colorsAbbrev, id)
        pendingHcEntry = entry
        viewModelScope.launch {
            try {
                fileRepository.appendHighContrastEntry(uri, entry)
                SyncHelper.notifyDataChanged()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save HC entry: ${e.message}"
            }
        }
    }

    fun stopHcEntry() {
        val uri = prefsRepository.fileUri ?: return
        val entry = pendingHcEntry ?: return
        val entryId = entry.id ?: return
        val endTime = LocalTime.now().withSecond(0).withNano(0)
        val completed = entry.copy(endTime = endTime)
        val completedLine = EntryParser.formatHighContrastEntry(completed)
        pendingHcEntry = null
        viewModelScope.launch {
            try {
                fileRepository.replaceById(uri, entryId, completedLine)
                SyncHelper.notifyDataChanged()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update HC entry: ${e.message}"
            }
        }
    }

    fun startNoise(settings: NoiseSettings) {
        val ctx = getApplication<Application>()
        val uri = prefsRepository.fileUri ?: return

        // Save prefs
        prefsRepository.lastNoiseType = settings.noiseType.name
        prefsRepository.noiseVolume = settings.volume
        prefsRepository.noiseFadeIn = fadeSecondsToIndex(settings.fadeInSeconds)
        prefsRepository.noiseFadeOut = fadeSecondsToIndex(settings.fadeOutSeconds)

        // Start service
        val intent = Intent(ctx, WhiteNoiseService::class.java).apply {
            putExtra(WhiteNoiseService.EXTRA_NOISE_TYPE, settings.noiseType.name)
            putExtra(WhiteNoiseService.EXTRA_VOLUME, settings.volume)
            putExtra(WhiteNoiseService.EXTRA_FADE_IN, settings.fadeInSeconds)
            putExtra(WhiteNoiseService.EXTRA_FADE_OUT, settings.fadeOutSeconds)
            putExtra(WhiteNoiseService.EXTRA_DURATION_MS, settings.durationMs)
        }
        ctx.startForegroundService(intent)

        // Append ongoing entry with pre-generated ID for reliable update later
        val now = LocalTime.now().withSecond(0).withNano(0)
        val entryId = EntryParser.generateId()
        val entry = WhiteNoiseEntry(settings.noiseType, LocalDate.now(), now, null, entryId)
        pendingNoiseEntry = entry
        viewModelScope.launch {
            try {
                fileRepository.appendWhiteNoiseEntry(uri, entry)
                SyncHelper.notifyDataChanged()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save noise entry: ${e.message}"
            }
        }
    }

    fun stopNoise() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, WhiteNoiseService::class.java).apply {
            action = WhiteNoiseService.ACTION_STOP
        }
        ctx.startService(intent)
    }

    private fun observeSyncCompleted() {
        viewModelScope.launch {
            SyncHelper.syncCompleted.collect {
                val uri = prefsRepository.fileUri ?: return@collect
                refreshTodayStatsInternal(uri)
            }
        }
    }

    private fun closeStaleOngoingEntries() {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            try {
                val data = fileRepository.readAll(uri)
                var changed = false

                // Close any ongoing HC entries (HC viewer is never active on fresh app start)
                data.highContrastEntries.filter { it.isOngoing }.forEach { entry ->
                    val completed = entry.copy(endTime = entry.startTime)
                    val completedLine = EntryParser.formatHighContrastEntry(completed)
                    if (entry.id != null) {
                        fileRepository.replaceById(uri, entry.id, completedLine)
                    } else {
                        val ongoingLine = EntryParser.stripId(EntryParser.formatHighContrastEntry(entry))
                        fileRepository.updateEntry(uri, ongoingLine, EntryParser.stripId(completedLine))
                    }
                    changed = true
                }

                // Close ongoing NOISE entries if the service is not currently playing
                val noiseIsPlaying = WhiteNoiseService.serviceState.value is NoiseServiceState.Playing
                val ongoingNoise = data.whiteNoiseEntries.filter { it.isOngoing }
                if (noiseIsPlaying && ongoingNoise.isNotEmpty()) {
                    // Service survived restart — restore pendingNoiseEntry so it can be completed later
                    pendingNoiseEntry = ongoingNoise.last()
                } else {
                    ongoingNoise.forEach { entry ->
                        val completed = entry.copy(endTime = entry.startTime)
                        val completedLine = EntryParser.formatWhiteNoiseEntry(completed)
                        if (entry.id != null) {
                            fileRepository.replaceById(uri, entry.id, completedLine)
                        } else {
                            val ongoingLine = EntryParser.stripId(EntryParser.formatWhiteNoiseEntry(entry))
                            fileRepository.updateEntry(uri, ongoingLine, EntryParser.stripId(completedLine))
                        }
                        changed = true
                    }
                }

                if (changed) {
                    SyncHelper.notifyDataChanged()
                }
            } catch (_: Exception) {}
        }
    }

    private fun observeNoiseState() {
        noiseObserverJob = viewModelScope.launch {
            var wasPlaying = false
            WhiteNoiseService.serviceState.collect { state ->
                if (wasPlaying && state is NoiseServiceState.Idle) {
                    // Noise auto-stopped or was stopped — update ongoing entry with end time
                    completeNoiseEntry()
                }
                wasPlaying = state is NoiseServiceState.Playing
            }
        }
    }

    private fun completeNoiseEntry() {
        val uri = prefsRepository.fileUri ?: return
        val entry = pendingNoiseEntry ?: return
        val entryId = entry.id ?: return
        val endTime = LocalTime.now().withSecond(0).withNano(0)
        val completedEntry = entry.copy(endTime = endTime)
        val completedLine = EntryParser.formatWhiteNoiseEntry(completedEntry)
        pendingNoiseEntry = null
        viewModelScope.launch {
            try {
                fileRepository.replaceById(uri, entryId, completedLine)
                SyncHelper.notifyDataChanged()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update noise entry: ${e.message}"
            }
        }
    }

    private fun fadeSecondsToIndex(seconds: Float): Int = when (seconds) {
        5f -> 1
        15f -> 2
        30f -> 3
        60f -> 4
        else -> 0
    }

    override fun onCleared() {
        super.onCleared()
        telemetryManager.stop()
    }
}
