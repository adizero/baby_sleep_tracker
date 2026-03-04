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
import java.time.LocalTime

data class SummaryStats(
    val avgSleepPerDay: Duration = Duration.ZERO,
    val avgNapsPerDay: Float = 0f,
    val avgDiapersPerDay: Float = 0f,
    val longestNap: Duration = Duration.ZERO,
    val shortestNap: Duration = Duration.ZERO
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val prefsRepository = PreferencesRepository(application)

    private val _dayStats = MutableStateFlow<List<DayStats>>(emptyList())
    val dayStats: StateFlow<List<DayStats>> = _dayStats

    private val _summaryStats = MutableStateFlow(SummaryStats())
    val summaryStats: StateFlow<SummaryStats> = _summaryStats

    private val _movingAverage = MutableStateFlow<List<Float>>(emptyList())
    val movingAverage: StateFlow<List<Float>> = _movingAverage

    private val _daysBack = MutableStateFlow(7)
    val daysBack: StateFlow<Int> = _daysBack

    companion object {
        private val DAY_START = LocalTime.of(7, 0)
        private val DAY_END = LocalTime.of(19, 0)
    }

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
            val data = fileRepository.readAll(uri)
            val sleepEntries = data.sleepEntries
            val diaperEntries = data.diaperEntries
            val today = LocalDate.now()
            val startDate = today.minusDays(_daysBack.value.toLong() - 1)

            val statsList = (0 until _daysBack.value).map { offset ->
                val date = startDate.plusDays(offset.toLong())
                val daySleep = sleepEntries.filter { it.date == date }
                val dayDiapers = diaperEntries.filter { it.date == date }

                var daySleepDur = Duration.ZERO
                var nightSleepDur = Duration.ZERO
                var longest = Duration.ZERO
                var shortest: Duration? = null

                daySleep.forEach { entry ->
                    val dur = entry.duration
                    if (dur > longest) longest = dur
                    if (shortest == null || dur < shortest!!) shortest = dur

                    if (entry.startTime >= DAY_START && entry.startTime < DAY_END) {
                        daySleepDur = daySleepDur.plus(dur)
                    } else {
                        nightSleepDur = nightSleepDur.plus(dur)
                    }
                }

                DayStats(
                    date = date,
                    totalSleep = daySleep.fold(Duration.ZERO) { acc, e -> acc.plus(e.duration) },
                    sleepCount = daySleep.size,
                    peeCount = dayDiapers.count { it.type == DiaperType.PEE },
                    pooCount = dayDiapers.count { it.type == DiaperType.POO },
                    peepooCount = dayDiapers.count { it.type == DiaperType.PEEPOO },
                    daySleep = daySleepDur,
                    nightSleep = nightSleepDur,
                    longestNap = longest,
                    shortestNap = shortest ?: Duration.ZERO
                )
            }

            _dayStats.value = statsList

            val daysWithData = statsList.filter { it.sleepCount > 0 || it.totalDiapers > 0 }
            val totalDays = daysWithData.size.coerceAtLeast(1)
            val allNaps = statsList.filter { it.longestNap > Duration.ZERO }

            _summaryStats.value = SummaryStats(
                avgSleepPerDay = statsList.fold(Duration.ZERO) { acc, s ->
                    acc.plus(s.totalSleep)
                }.dividedBy(totalDays.toLong()),
                avgNapsPerDay = statsList.sumOf { it.sleepCount }.toFloat() / totalDays,
                avgDiapersPerDay = statsList.sumOf { it.totalDiapers }.toFloat() / totalDays,
                longestNap = allNaps.maxOfOrNull { it.longestNap } ?: Duration.ZERO,
                shortestNap = allNaps.filter { it.shortestNap > Duration.ZERO }
                    .minOfOrNull { it.shortestNap } ?: Duration.ZERO
            )

            val minutes = statsList.map { it.totalSleep.toMinutes().toFloat() }
            val ma = minutes.mapIndexed { i, _ ->
                val start = (i - 1).coerceAtLeast(0)
                val end = (i + 1).coerceAtMost(minutes.lastIndex)
                val window = minutes.subList(start, end + 1)
                window.average().toFloat() / 60f
            }
            _movingAverage.value = ma
        }
    }
}
