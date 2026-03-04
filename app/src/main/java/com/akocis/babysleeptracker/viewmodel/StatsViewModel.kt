package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.DayStats
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val prefsRepository = PreferencesRepository(application)

    private val _dayStats = MutableStateFlow<List<DayStats>>(emptyList())
    val dayStats: StateFlow<List<DayStats>> = _dayStats

    private val _daysBack = MutableStateFlow(7)
    val daysBack: StateFlow<Int> = _daysBack

    init {
        loadStats()
    }

    fun setDaysBack(days: Int) {
        _daysBack.value = days
        loadStats()
    }

    fun loadStats() {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            val (sleepEntries, diaperEntries) = fileRepository.readAll(uri)
            val today = LocalDate.now()
            val startDate = today.minusDays(_daysBack.value.toLong() - 1)

            val statsList = (0 until _daysBack.value).map { offset ->
                val date = startDate.plusDays(offset.toLong())
                val daySleep = sleepEntries.filter { it.date == date }
                val dayDiapers = diaperEntries.filter { it.date == date }

                DayStats(
                    date = date,
                    totalSleep = daySleep.fold(Duration.ZERO) { acc, e -> acc.plus(e.duration) },
                    sleepCount = daySleep.size,
                    peeCount = dayDiapers.count { it.type == DiaperType.PEE },
                    pooCount = dayDiapers.count { it.type == DiaperType.POO },
                    peepooCount = dayDiapers.count { it.type == DiaperType.PEEPOO }
                )
            }

            _dayStats.value = statsList
        }
    }
}
