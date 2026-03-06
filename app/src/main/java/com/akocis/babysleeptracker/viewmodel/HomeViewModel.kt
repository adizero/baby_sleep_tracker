package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.ActivityType
import com.akocis.babysleeptracker.model.DayStats
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.FeedEntry
import com.akocis.babysleeptracker.model.FeedSide
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.model.TrackingState
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.repository.SyncHelper
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _babyName = MutableStateFlow<String?>(null)
    val babyName: StateFlow<String?> = _babyName

    private val _babyAge = MutableStateFlow<String?>(null)
    val babyAge: StateFlow<String?> = _babyAge

    private var timerJob: Job? = null
    private var undoDismissJob: Job? = null
    private var pendingWrite = false

    init {
        _trackingState.value = prefsRepository.loadTrackingState()
        _hasFile.value = prefsRepository.fileUri != null
        if (_trackingState.value is TrackingState.Sleeping || _trackingState.value is TrackingState.Feeding) {
            startTimer()
        }
        loadBabyInfo()
        syncAndRefresh()
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
                viewModelScope.launch {
                    try {
                        val ongoingLine = "SLEEP ${state.startDate.format(DateTimeUtil.DATE_FORMAT)} ${state.startTime.format(DateTimeUtil.TIME_FORMAT)} -"
                        val completedLine = "SLEEP ${state.startDate.format(DateTimeUtil.DATE_FORMAT)} ${state.startTime.format(DateTimeUtil.TIME_FORMAT)} - ${endTime.format(DateTimeUtil.TIME_FORMAT)}"
                        fileRepository.updateEntry(uri, ongoingLine, completedLine)
                        _trackingState.value = TrackingState.Idle
                        prefsRepository.saveTrackingState(TrackingState.Idle)
                        stopTimer()
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
                stopTimer()
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

    fun syncAndRefresh() {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            SyncHelper.pullLatest()
            refreshTodayStatsInternal(uri)
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

        val todayDiapers = data.diaperEntries.filter { it.date == today }

        _todayStats.value = DayStats(
            date = today,
            totalSleep = todaySleepDuration,
            sleepCount = data.sleepEntries.count { it.date == today },
            peeCount = todayDiapers.count { it.type == DiaperType.PEE },
            pooCount = todayDiapers.count { it.type == DiaperType.POO },
            peepooCount = todayDiapers.count { it.type == DiaperType.PEEPOO },
            feedCount = data.feedEntries.count { it.date == today },
            totalFeedDuration = todayFeedDuration
        )

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
                    stopTimer()
                }
            }
        }

        if (data.babyName != null) {
            _babyName.value = data.babyName
            prefsRepository.babyName = data.babyName
        }
        if (data.babyBirthDate != null) {
            _babyAge.value = formatAge(data.babyBirthDate)
            prefsRepository.babyBirthDate = data.babyBirthDate
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
}
