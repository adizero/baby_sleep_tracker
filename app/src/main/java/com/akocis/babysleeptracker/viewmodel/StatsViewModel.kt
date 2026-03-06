package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.ActivityType
import com.akocis.babysleeptracker.model.BottleType
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
    val avgFeedSessionsPerDay: Float = 0f,
    val avgDonorMlPerDay: Float = 0f,
    val avgFormulaMlPerDay: Float = 0f,
    val avgDonorCountPerDay: Float = 0f,
    val avgFormulaCountPerDay: Float = 0f,
    val avgPumpedMlPerDay: Float = 0f,
    val avgPumpedCountPerDay: Float = 0f
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
            val bottleFeedEntries = data.bottleFeedEntries
            val activityEntries = data.activityEntries
            val today = LocalDate.now()

            if (_is24hMode.value) {
                load24hStats(sleepEntries, diaperEntries, feedEntries, bottleFeedEntries, activityEntries)
            } else {
                loadDayRangeStats(sleepEntries, diaperEntries, feedEntries, bottleFeedEntries, activityEntries, today)
            }
        }
    }

    private fun load24hStats(
        sleepEntries: List<com.akocis.babysleeptracker.model.SleepEntry>,
        diaperEntries: List<com.akocis.babysleeptracker.model.DiaperEntry>,
        feedEntries: List<com.akocis.babysleeptracker.model.FeedEntry>,
        bottleFeedEntries: List<com.akocis.babysleeptracker.model.BottleFeedEntry> = emptyList(),
        activityEntries: List<com.akocis.babysleeptracker.model.ActivityEntry> = emptyList()
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
        val recentBottle = bottleFeedEntries.filter {
            it.date.atTime(it.time) >= cutoff
        }
        val recentActivities = activityEntries.filter {
            it.date.atTime(it.time) >= cutoff
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

            val periodBottle = recentBottle.filter {
                val dt = it.date.atTime(it.time)
                dt >= periodStart && dt < periodEnd
            }
            val periodActivities = recentActivities.filter {
                val dt = it.date.atTime(it.time)
                dt >= periodStart && dt < periodEnd
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
                donorCount = periodBottle.count { it.type == BottleType.DONOR },
                donorMl = periodBottle.filter { it.type == BottleType.DONOR }.sumOf { it.amountMl },
                formulaCount = periodBottle.count { it.type == BottleType.FORMULA },
                formulaMl = periodBottle.filter { it.type == BottleType.FORMULA }.sumOf { it.amountMl },
                pumpedCount = periodBottle.count { it.type == BottleType.PUMPED },
                pumpedMl = periodBottle.filter { it.type == BottleType.PUMPED }.sumOf { it.amountMl },
                strollerCount = periodActivities.count { it.type == ActivityType.STROLLER },
                bathCount = periodActivities.count { it.type == ActivityType.BATH },
                noteCount = periodActivities.count { it.type == ActivityType.NOTE },
                label = label
            )
        }

        _dayStats.value = periodStats

        // Summary across all 24h — use overlap to clip to the actual window
        val totalSleepIn24h = recentSleep.fold(Duration.ZERO) { acc, entry ->
            val entryStart = entry.date.atTime(entry.startTime)
            acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), cutoff, now))
        }
        val totalFeedIn24h = recentFeeds.fold(Duration.ZERO) { acc, entry ->
            val entryStart = entry.date.atTime(entry.startTime)
            acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), cutoff, now))
        }

        // Count only entries that start within the 24h window
        val sleepCountIn24h = recentSleep.count {
            val dt = it.date.atTime(it.startTime)
            dt >= cutoff && dt < now
        }
        val feedCountIn24h = recentFeeds.count {
            val dt = it.date.atTime(it.startTime)
            dt >= cutoff && dt < now
        }

        var totalLongest = Duration.ZERO
        var totalShortest: Duration? = null
        recentSleep.filter {
            val dt = it.date.atTime(it.startTime)
            dt >= cutoff && dt < now
        }.forEach { entry ->
            val dur = entry.duration
            if (dur > totalLongest) totalLongest = dur
            if (totalShortest == null || dur < totalShortest!!) totalShortest = dur
        }

        _summaryStats.value = SummaryStats(
            avgSleepPerDay = totalSleepIn24h,
            avgNapsPerDay = sleepCountIn24h.toFloat(),
            avgDiapersPerDay = recentDiapers.size.toFloat(),
            longestNap = totalLongest,
            shortestNap = totalShortest ?: Duration.ZERO,
            avgFeedPerDay = totalFeedIn24h,
            avgFeedSessionsPerDay = feedCountIn24h.toFloat(),
            avgDonorMlPerDay = recentBottle.filter { it.type == BottleType.DONOR }.sumOf { it.amountMl }.toFloat(),
            avgFormulaMlPerDay = recentBottle.filter { it.type == BottleType.FORMULA }.sumOf { it.amountMl }.toFloat(),
            avgDonorCountPerDay = recentBottle.count { it.type == BottleType.DONOR }.toFloat(),
            avgFormulaCountPerDay = recentBottle.count { it.type == BottleType.FORMULA }.toFloat(),
            avgPumpedMlPerDay = recentBottle.filter { it.type == BottleType.PUMPED }.sumOf { it.amountMl }.toFloat(),
            avgPumpedCountPerDay = recentBottle.count { it.type == BottleType.PUMPED }.toFloat()
        )
        _movingAverage.value = emptyList()
        _feedMovingAverage.value = emptyList()
    }

    private fun loadDayRangeStats(
        sleepEntries: List<com.akocis.babysleeptracker.model.SleepEntry>,
        diaperEntries: List<com.akocis.babysleeptracker.model.DiaperEntry>,
        feedEntries: List<com.akocis.babysleeptracker.model.FeedEntry>,
        bottleFeedEntries: List<com.akocis.babysleeptracker.model.BottleFeedEntry> = emptyList(),
        activityEntries: List<com.akocis.babysleeptracker.model.ActivityEntry> = emptyList(),
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

            val dayBottle = bottleFeedEntries.filter { it.date == date }
            val dayActivities = activityEntries.filter { it.date == date }

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
                totalFeedDuration = totalFeedDuration,
                donorCount = dayBottle.count { it.type == BottleType.DONOR },
                donorMl = dayBottle.filter { it.type == BottleType.DONOR }.sumOf { it.amountMl },
                formulaCount = dayBottle.count { it.type == BottleType.FORMULA },
                formulaMl = dayBottle.filter { it.type == BottleType.FORMULA }.sumOf { it.amountMl },
                pumpedCount = dayBottle.count { it.type == BottleType.PUMPED },
                pumpedMl = dayBottle.filter { it.type == BottleType.PUMPED }.sumOf { it.amountMl },
                strollerCount = dayActivities.count { it.type == ActivityType.STROLLER },
                bathCount = dayActivities.count { it.type == ActivityType.BATH },
                noteCount = dayActivities.count { it.type == ActivityType.NOTE }
            )
        }

        _dayStats.value = statsList

        val daysWithData = statsList.filter { it.sleepCount > 0 || it.totalDiapers > 0 || it.feedCount > 0 || it.totalBottleFeeds > 0 || it.totalActivities > 0 }
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
            avgFeedSessionsPerDay = statsList.sumOf { it.feedCount }.toFloat() / totalDays,
            avgDonorMlPerDay = statsList.sumOf { it.donorMl }.toFloat() / totalDays,
            avgFormulaMlPerDay = statsList.sumOf { it.formulaMl }.toFloat() / totalDays,
            avgDonorCountPerDay = statsList.sumOf { it.donorCount }.toFloat() / totalDays,
            avgFormulaCountPerDay = statsList.sumOf { it.formulaCount }.toFloat() / totalDays,
            avgPumpedMlPerDay = statsList.sumOf { it.pumpedMl }.toFloat() / totalDays,
            avgPumpedCountPerDay = statsList.sumOf { it.pumpedCount }.toFloat() / totalDays
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
