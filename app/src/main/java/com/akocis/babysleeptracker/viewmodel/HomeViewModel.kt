package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.DayStats
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.model.TrackingState
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.util.DateTimeUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

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

    private var timerJob: Job? = null

    init {
        _trackingState.value = prefsRepository.loadTrackingState()
        _hasFile.value = prefsRepository.fileUri != null
        if (_trackingState.value is TrackingState.Sleeping) {
            startTimer()
        }
        refreshTodayStats()
    }

    fun toggleSleep() {
        val uri = prefsRepository.fileUri ?: return
        when (val state = _trackingState.value) {
            is TrackingState.Idle -> {
                val now = TrackingState.Sleeping(LocalDate.now(), LocalTime.now().withSecond(0).withNano(0))
                _trackingState.value = now
                prefsRepository.saveTrackingState(now)
                startTimer()
            }
            is TrackingState.Sleeping -> {
                val endTime = LocalTime.now().withSecond(0).withNano(0)
                val entry = SleepEntry(state.startDate, state.startTime, endTime)
                viewModelScope.launch {
                    fileRepository.appendSleepEntry(uri, entry)
                    _trackingState.value = TrackingState.Idle
                    prefsRepository.saveTrackingState(TrackingState.Idle)
                    stopTimer()
                    refreshTodayStats()
                }
            }
        }
    }

    fun logDiaper(type: DiaperType) {
        val uri = prefsRepository.fileUri ?: return
        val entry = DiaperEntry(type, LocalDate.now(), LocalTime.now().withSecond(0).withNano(0))
        viewModelScope.launch {
            fileRepository.appendDiaperEntry(uri, entry)
            refreshTodayStats()
        }
    }

    fun refreshTodayStats() {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            val (sleepEntries, diaperEntries) = fileRepository.readAll(uri)
            val today = LocalDate.now()

            val todaySleep = sleepEntries.filter { it.date == today }
            val todayDiapers = diaperEntries.filter { it.date == today }

            _todayStats.value = DayStats(
                date = today,
                totalSleep = todaySleep.fold(Duration.ZERO) { acc, e -> acc.plus(e.duration) },
                sleepCount = todaySleep.size,
                peeCount = todayDiapers.count { it.type == DiaperType.PEE },
                pooCount = todayDiapers.count { it.type == DiaperType.POO },
                peepooCount = todayDiapers.count { it.type == DiaperType.PEEPOO }
            )
        }
    }

    fun onFileSelected(uri: Uri) {
        prefsRepository.fileUri = uri
        _hasFile.value = true
        refreshTodayStats()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val state = _trackingState.value
                if (state is TrackingState.Sleeping) {
                    _elapsedTime.value = DateTimeUtil.formatElapsed(state.startDate, state.startTime)
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
