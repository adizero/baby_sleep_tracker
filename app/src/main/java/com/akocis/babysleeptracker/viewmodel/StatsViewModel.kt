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
import java.time.temporal.ChronoUnit

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

    /** true when daysBack<=0 (rolling window modes: 0=24h, -3=72h) */
    private val _isRollingMode = MutableStateFlow(false)
    val isRollingMode: StateFlow<Boolean> = _isRollingMode

    private val _hourlyStats = MutableStateFlow<List<DayStats>>(emptyList())
    val hourlyStats: StateFlow<List<DayStats>> = _hourlyStats

    private val dayStart: LocalTime get() = LocalTime.of(prefsRepository.dayStartHour, 0)
    private val dayEnd: LocalTime get() = LocalTime.of(prefsRepository.dayEndHour, 0)

    init {
        loadStats()
    }

    fun setDaysBack(days: Int) {
        _daysBack.value = days
        _isRollingMode.value = days <= 0
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

            if (_daysBack.value <= 0) {
                val hours = if (_daysBack.value == -3) 72 else 24
                loadRollingStats(hours, sleepEntries, diaperEntries, feedEntries, bottleFeedEntries, activityEntries)
            } else {
                loadDayRangeStats(sleepEntries, diaperEntries, feedEntries, bottleFeedEntries, activityEntries, today)
            }
        }
    }

    private fun loadRollingStats(
        hours: Int,
        sleepEntries: List<com.akocis.babysleeptracker.model.SleepEntry>,
        diaperEntries: List<com.akocis.babysleeptracker.model.DiaperEntry>,
        feedEntries: List<com.akocis.babysleeptracker.model.FeedEntry>,
        bottleFeedEntries: List<com.akocis.babysleeptracker.model.BottleFeedEntry> = emptyList(),
        activityEntries: List<com.akocis.babysleeptracker.model.ActivityEntry> = emptyList()
    ) {
        val now = LocalDateTime.now()
        val cutoff = now.minusHours(hours.toLong())

        // Include entries that could overlap with the window (started up to 24h before cutoff)
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

        // Build 4-hour period stats: 24h → 6 periods, 72h → 18 periods
        val periodCount = hours / 4
        val periodStats = (0 until periodCount).map { periodIndex ->
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
                if (entry.startTime >= dayStart && entry.startTime < dayEnd) {
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

        // Summary across the rolling window
        val totalSleepInWindow = recentSleep.fold(Duration.ZERO) { acc, entry ->
            val entryStart = entry.date.atTime(entry.startTime)
            acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), cutoff, now))
        }
        val totalFeedInWindow = recentFeeds.fold(Duration.ZERO) { acc, entry ->
            val entryStart = entry.date.atTime(entry.startTime)
            acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), cutoff, now))
        }

        val sleepCountInWindow = recentSleep.count {
            val dt = it.date.atTime(it.startTime)
            dt >= cutoff && dt < now
        }
        val feedCountInWindow = recentFeeds.count {
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
            avgSleepPerDay = totalSleepInWindow,
            avgNapsPerDay = sleepCountInWindow.toFloat(),
            avgDiapersPerDay = recentDiapers.size.toFloat(),
            longestNap = totalLongest,
            shortestNap = totalShortest ?: Duration.ZERO,
            avgFeedPerDay = totalFeedInWindow,
            avgFeedSessionsPerDay = feedCountInWindow.toFloat(),
            avgDonorMlPerDay = recentBottle.filter { it.type == BottleType.DONOR }.sumOf { it.amountMl }.toFloat(),
            avgFormulaMlPerDay = recentBottle.filter { it.type == BottleType.FORMULA }.sumOf { it.amountMl }.toFloat(),
            avgDonorCountPerDay = recentBottle.count { it.type == BottleType.DONOR }.toFloat(),
            avgFormulaCountPerDay = recentBottle.count { it.type == BottleType.FORMULA }.toFloat(),
            avgPumpedMlPerDay = recentBottle.filter { it.type == BottleType.PUMPED }.sumOf { it.amountMl }.toFloat(),
            avgPumpedCountPerDay = recentBottle.count { it.type == BottleType.PUMPED }.toFloat()
        )
        _movingAverage.value = emptyList()
        _feedMovingAverage.value = emptyList()

        // Hourly stats: aggregate into 24 one-hour buckets
        computeRollingHourlyStats(cutoff, now, recentSleep, recentFeeds, recentDiapers, recentBottle, recentActivities)
    }

    private fun computeRollingHourlyStats(
        cutoff: LocalDateTime,
        now: LocalDateTime,
        sleepEntries: List<com.akocis.babysleeptracker.model.SleepEntry>,
        feedEntries: List<com.akocis.babysleeptracker.model.FeedEntry>,
        diaperEntries: List<com.akocis.babysleeptracker.model.DiaperEntry>,
        bottleFeedEntries: List<com.akocis.babysleeptracker.model.BottleFeedEntry>,
        activityEntries: List<com.akocis.babysleeptracker.model.ActivityEntry>
    ) {
        // Build clock-aligned hour buckets across the rolling window, then collapse into 24 hours
        val sleepByHour = Array(24) { Duration.ZERO }
        val feedByHour = Array(24) { Duration.ZERO }
        val diapersByHour = Array(24) { mutableListOf<com.akocis.babysleeptracker.model.DiaperEntry>() }
        val bottleByHour = Array(24) { mutableListOf<com.akocis.babysleeptracker.model.BottleFeedEntry>() }

        // Walk clock-aligned hour buckets, clipping to the actual window
        var bucketStart = cutoff.truncatedTo(ChronoUnit.HOURS)
        while (bucketStart < now) {
            val bucketEnd = bucketStart.plusHours(1)
            val effectiveStart = if (bucketStart < cutoff) cutoff else bucketStart
            val effectiveEnd = if (bucketEnd > now) now else bucketEnd
            val hour = bucketStart.hour

            if (effectiveStart < effectiveEnd) {
                sleepEntries.forEach { entry ->
                    val entryStart = entry.date.atTime(entry.startTime)
                    val overlap = DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), effectiveStart, effectiveEnd)
                    if (overlap > Duration.ZERO) sleepByHour[hour] = sleepByHour[hour].plus(overlap)
                }
                feedEntries.forEach { entry ->
                    val entryStart = entry.date.atTime(entry.startTime)
                    val overlap = DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), effectiveStart, effectiveEnd)
                    if (overlap > Duration.ZERO) feedByHour[hour] = feedByHour[hour].plus(overlap)
                }
            }

            bucketStart = bucketStart.plusHours(1)
        }

        // Point events: attribute to the hour they occur in
        diaperEntries.forEach { entry ->
            val dt = entry.date.atTime(entry.time)
            if (dt >= cutoff && dt < now) diapersByHour[dt.hour].add(entry)
        }
        bottleFeedEntries.forEach { entry ->
            val dt = entry.date.atTime(entry.time)
            if (dt >= cutoff && dt < now) bottleByHour[dt.hour].add(entry)
        }

        _hourlyStats.value = (0 until 24).map { hour ->
            val hDiapers = diapersByHour[hour]
            val hBottle = bottleByHour[hour]
            DayStats(
                date = LocalDate.now(),
                totalSleep = sleepByHour[hour],
                sleepCount = 0,
                peeCount = hDiapers.count { it.type == DiaperType.PEE },
                pooCount = hDiapers.count { it.type == DiaperType.POO },
                peepooCount = hDiapers.count { it.type == DiaperType.PEEPOO },
                totalFeedDuration = feedByHour[hour],
                donorCount = hBottle.count { it.type == BottleType.DONOR },
                donorMl = hBottle.filter { it.type == BottleType.DONOR }.sumOf { it.amountMl },
                formulaCount = hBottle.count { it.type == BottleType.FORMULA },
                formulaMl = hBottle.filter { it.type == BottleType.FORMULA }.sumOf { it.amountMl },
                pumpedCount = hBottle.count { it.type == BottleType.PUMPED },
                pumpedMl = hBottle.filter { it.type == BottleType.PUMPED }.sumOf { it.amountMl },
                label = hour.toString()
            )
        }
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
            val dayStartDt = date.atStartOfDay()
            val dayEndDt = date.plusDays(1).atStartOfDay()

            // Duration: split across day boundaries (consider entries from previous day)
            val relevantSleep = sleepEntries.filter { it.date == date || it.date == date.minusDays(1) }
            val totalSleep = relevantSleep.fold(Duration.ZERO) { acc, entry ->
                val entryStart = entry.date.atTime(entry.startTime)
                acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), dayStartDt, dayEndDt))
            }

            val relevantFeeds = feedEntries.filter { it.date == date || it.date == date.minusDays(1) }
            val totalFeedDuration = relevantFeeds.fold(Duration.ZERO) { acc, entry ->
                val entryStart = entry.date.atTime(entry.startTime)
                acc.plus(DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), dayStartDt, dayEndDt))
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
                if (entry.startTime >= dayStart && entry.startTime < dayEnd) {
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

        // Hourly stats: aggregate across all days into 24 hourly buckets, divide by day count
        computeDayRangeHourlyStats(totalDays, startDate, today, sleepEntries, feedEntries, diaperEntries, bottleFeedEntries)
    }

    private fun computeDayRangeHourlyStats(
        totalDays: Int,
        startDate: LocalDate,
        endDate: LocalDate,
        sleepEntries: List<com.akocis.babysleeptracker.model.SleepEntry>,
        feedEntries: List<com.akocis.babysleeptracker.model.FeedEntry>,
        diaperEntries: List<com.akocis.babysleeptracker.model.DiaperEntry>,
        bottleFeedEntries: List<com.akocis.babysleeptracker.model.BottleFeedEntry>
    ) {
        val sleepByHour = Array(24) { Duration.ZERO }
        val feedByHour = Array(24) { Duration.ZERO }
        val diapersByHour = Array(24) { mutableListOf<com.akocis.babysleeptracker.model.DiaperEntry>() }
        val bottleByHour = Array(24) { mutableListOf<com.akocis.babysleeptracker.model.BottleFeedEntry>() }

        // Walk each day, each hour
        var date = startDate
        while (!date.isAfter(endDate)) {
            for (hour in 0 until 24) {
                val bucketStart = date.atTime(hour, 0)
                val bucketEnd = bucketStart.plusHours(1)

                // Only consider sleep/feed entries from this day or previous day
                sleepEntries.filter { it.date == date || it.date == date.minusDays(1) }.forEach { entry ->
                    val entryStart = entry.date.atTime(entry.startTime)
                    val overlap = DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), bucketStart, bucketEnd)
                    if (overlap > Duration.ZERO) sleepByHour[hour] = sleepByHour[hour].plus(overlap)
                }
                feedEntries.filter { it.date == date || it.date == date.minusDays(1) }.forEach { entry ->
                    val entryStart = entry.date.atTime(entry.startTime)
                    val overlap = DateTimeUtil.overlapDuration(entryStart, entryStart.plus(entry.duration), bucketStart, bucketEnd)
                    if (overlap > Duration.ZERO) feedByHour[hour] = feedByHour[hour].plus(overlap)
                }
            }
            date = date.plusDays(1)
        }

        // Point events
        diaperEntries.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }.forEach { entry ->
            diapersByHour[entry.time.hour].add(entry)
        }
        bottleFeedEntries.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }.forEach { entry ->
            bottleByHour[entry.time.hour].add(entry)
        }

        val divisor = totalDays.toLong().coerceAtLeast(1)
        _hourlyStats.value = (0 until 24).map { hour ->
            val hDiapers = diapersByHour[hour]
            val hBottle = bottleByHour[hour]
            DayStats(
                date = LocalDate.now(),
                totalSleep = sleepByHour[hour].dividedBy(divisor),
                sleepCount = 0,
                peeCount = hDiapers.count { it.type == DiaperType.PEE },
                pooCount = hDiapers.count { it.type == DiaperType.POO },
                peepooCount = hDiapers.count { it.type == DiaperType.PEEPOO },
                totalFeedDuration = feedByHour[hour].dividedBy(divisor),
                donorCount = hBottle.count { it.type == BottleType.DONOR },
                donorMl = hBottle.filter { it.type == BottleType.DONOR }.sumOf { it.amountMl },
                formulaCount = hBottle.count { it.type == BottleType.FORMULA },
                formulaMl = hBottle.filter { it.type == BottleType.FORMULA }.sumOf { it.amountMl },
                pumpedCount = hBottle.count { it.type == BottleType.PUMPED },
                pumpedMl = hBottle.filter { it.type == BottleType.PUMPED }.sumOf { it.amountMl },
                label = hour.toString()
            )
        }
    }
}
