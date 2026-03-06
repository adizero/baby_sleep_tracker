package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.DayStats
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.util.DateTimeUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class SummaryStats(
    val avgSleepPerDay: Duration = Duration.ZERO,
    val avgNapsPerDay: Float = 0f,
    val avgDiapersPerDay: Float = 0f,
    val longestNap: Duration = Duration.ZERO,
    val shortestNap: Duration = Duration.ZERO,
    val avgFeedPerDay: Duration = Duration.ZERO,
    val avgFeedSessionsPerDay: Float = 0f
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

    private val _feedMovingAverage = MutableStateFlow<List<Float>>(emptyList())
    val feedMovingAverage: StateFlow<List<Float>> = _feedMovingAverage

    private val _daysBack = MutableStateFlow(7)
    val daysBack: StateFlow<Int> = _daysBack

    /** true when daysBack==0 (last-24h mode) */
    private val _is24hMode = MutableStateFlow(false)
    val is24hMode: StateFlow<Boolean> = _is24hMode

    companion object {
        private val DAY_START = LocalTime.of(7, 0)
        private val DAY_END = LocalTime.of(19, 0)
    }

    init {
        loadStats()
    }

    fun setDaysBack(days: Int) {
        _daysBack.value = days
        _is24hMode.value = days == 0
        loadStats()
    }

    fun loadStats() {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            val data = fileRepository.readAll(uri)
            val sleepEntries = data.sleepEntries
            val diaperEntries = data.diaperEntries
            val feedEntries = data.feedEntries
            val today = LocalDate.now()

            if (_is24hMode.value) {
                load24hStats(sleepEntries, diaperEntries, feedEntries)
            } else {
                loadDayRangeStats(sleepEntries, diaperEntries, feedEntries, today)
            }
        }
    }

    private fun load24hStats(
        sleepEntries: List<com.akocis.babysleeptracker.model.SleepEntry>,
        diaperEntries: List<com.akocis.babysleeptracker.model.DiaperEntry>,
        feedEntries: List<com.akocis.babysleeptracker.model.FeedEntry>
    ) {
        val now = LocalDateTime.now()
        val cutoff = now.minusHours(24)

        // Include entries that could overlap with the 24h window (started up to 24h before cutoff)
        val extendedCutoff = cutoff.minusHours(24)
        val recentSleep = sleepEntries.filter {
            it.date.atTime(it.startTime) >= extendedCutoff
        }
        val recentDiapers = diaperEntries.filter {
            it.date.atTime(it.time) >= cutoff
        }
        val recentFeeds = feedEntries.filter {
            it.date.atTime(it.startTime) >= extendedCutoff
        }

        // Build 6 four-hour period stats
        val periodStats = (0 until 6).map { periodIndex ->
            val periodStart = cutoff.plusHours(periodIndex.toLong() * 4)
            val periodEnd = periodStart.plusHours(4)
            val label = "%02d-%02d".format(periodStart.hour, periodEnd.hour % 24)

            // Duration: split across period boundaries using overlap
            val periodSleepDuration = recentSleep.fold(Duration.ZERO) { acc, entry ->
                val entryStart = entry.date.atTime(entry.startTime)
                acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), periodStart, periodEnd))
            }
            val periodFeedDuration = recentFeeds.fold(Duration.ZERO) { acc, entry ->
                val entryStart = entry.date.atTime(entry.startTime)
                acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), periodStart, periodEnd))
            }

            // Counts: attributed to period where entry starts
            val periodSleepCount = recentSleep.count {
                val dt = it.date.atTime(it.startTime)
                dt >= periodStart && dt < periodEnd
            }
            val periodFeedCount = recentFeeds.count {
                val dt = it.date.atTime(it.startTime)
                dt >= periodStart && dt < periodEnd
            }
            val periodDiapers = recentDiapers.filter {
                val dt = it.date.atTime(it.time)
                dt >= periodStart && dt < periodEnd
            }

            // Longest/shortest from entries starting in this period
            var daySleepDur = Duration.ZERO
            var nightSleepDur = Duration.ZERO
            var longest = Duration.ZERO
            var shortest: Duration? = null

            recentSleep.filter {
                val dt = it.date.atTime(it.startTime)
                dt >= periodStart && dt < periodEnd
            }.forEach { entry ->
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
                date = periodStart.toLocalDate(),
                totalSleep = periodSleepDuration,
                sleepCount = periodSleepCount,
                peeCount = periodDiapers.count { it.type == DiaperType.PEE },
                pooCount = periodDiapers.count { it.type == DiaperType.POO },
                peepooCount = periodDiapers.count { it.type == DiaperType.PEEPOO },
                daySleep = daySleepDur,
                nightSleep = nightSleepDur,
                longestNap = longest,
                shortestNap = shortest ?: Duration.ZERO,
                feedCount = periodFeedCount,
                totalFeedDuration = periodFeedDuration,
                label = label
            )
        }

        _dayStats.value = periodStats

        // Summary across all 24h
        var totalLongest = Duration.ZERO
        var totalShortest: Duration? = null
        recentSleep.forEach { entry ->
            val dur = entry.duration
            if (dur > totalLongest) totalLongest = dur
            if (totalShortest == null || dur < totalShortest!!) totalShortest = dur
        }

        _summaryStats.value = SummaryStats(
            avgSleepPerDay = recentSleep.fold(Duration.ZERO) { acc, e -> acc.plus(e.duration) },
            avgNapsPerDay = recentSleep.size.toFloat(),
            avgDiapersPerDay = recentDiapers.size.toFloat(),
            longestNap = totalLongest,
            shortestNap = totalShortest ?: Duration.ZERO,
            avgFeedPerDay = recentFeeds.fold(Duration.ZERO) { acc, e -> acc.plus(e.duration) },
            avgFeedSessionsPerDay = recentFeeds.size.toFloat()
        )
        _movingAverage.value = emptyList()
        _feedMovingAverage.value = emptyList()
    }

    private fun loadDayRangeStats(
        sleepEntries: List<com.akocis.babysleeptracker.model.SleepEntry>,
        diaperEntries: List<com.akocis.babysleeptracker.model.DiaperEntry>,
        feedEntries: List<com.akocis.babysleeptracker.model.FeedEntry>,
        today: LocalDate
    ) {
        val days = _daysBack.value
        val startDate = today.minusDays(days.toLong() - 1)

        val statsList = (0 until days).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val dayStart = date.atStartOfDay()
            val dayEnd = date.plusDays(1).atStartOfDay()

            // Duration: split across day boundaries (consider entries from previous day)
            val relevantSleep = sleepEntries.filter { it.date == date || it.date == date.minusDays(1) }
            val totalSleep = relevantSleep.fold(Duration.ZERO) { acc, entry ->
                val entryStart = entry.date.atTime(entry.startTime)
                acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), dayStart, dayEnd))
            }

            val relevantFeeds = feedEntries.filter { it.date == date || it.date == date.minusDays(1) }
            val totalFeedDuration = relevantFeeds.fold(Duration.ZERO) { acc, entry ->
                val entryStart = entry.date.atTime(entry.startTime)
                acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), dayStart, dayEnd))
            }

            // Counts, longest/shortest, day/night: attributed to entries starting on this date
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
                totalSleep = totalSleep,
                sleepCount = daySleep.size,
                peeCount = dayDiapers.count { it.type == DiaperType.PEE },
                pooCount = dayDiapers.count { it.type == DiaperType.POO },
                peepooCount = dayDiapers.count { it.type == DiaperType.PEEPOO },
                daySleep = daySleepDur,
                nightSleep = nightSleepDur,
                longestNap = longest,
                shortestNap = shortest ?: Duration.ZERO,
                feedCount = feedEntries.count { it.date == date },
                totalFeedDuration = totalFeedDuration
            )
        }

        _dayStats.value = statsList

        val daysWithData = statsList.filter { it.sleepCount > 0 || it.totalDiapers > 0 || it.feedCount > 0 }
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
                .minOfOrNull { it.shortestNap } ?: Duration.ZERO,
            avgFeedPerDay = statsList.fold(Duration.ZERO) { acc, s ->
                acc.plus(s.totalFeedDuration)
            }.dividedBy(totalDays.toLong()),
            avgFeedSessionsPerDay = statsList.sumOf { it.feedCount }.toFloat() / totalDays
        )

        // Sleep moving average
        val minutes = statsList.map { it.totalSleep.toMinutes().toFloat() }
        val ma = minutes.mapIndexed { i, _ ->
            val start = (i - 1).coerceAtLeast(0)
            val end = (i + 1).coerceAtMost(minutes.lastIndex)
            val window = minutes.subList(start, end + 1)
            window.average().toFloat() / 60f
        }
        _movingAverage.value = ma

        // Feed moving average
        val feedMinutes = statsList.map { it.totalFeedDuration.toMinutes().toFloat() }
        val feedMa = feedMinutes.mapIndexed { i, _ ->
            val start = (i - 1).coerceAtLeast(0)
            val end = (i + 1).coerceAtMost(feedMinutes.lastIndex)
            val window = feedMinutes.subList(start, end + 1)
            window.average().toFloat() / 60f
        }
        _feedMovingAverage.value = feedMa
    }
}
