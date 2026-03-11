package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.BottleFeedEntry
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.FeedEntry
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.repository.SyncHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth

data class CalendarDayData(
    val date: LocalDate,
    val sleepEntries: List<SleepEntry> = emptyList(),
    val diaperEntries: List<DiaperEntry> = emptyList(),
    val activityEntries: List<ActivityEntry> = emptyList(),
    val feedEntries: List<FeedEntry> = emptyList(),
    val bottleFeedEntries: List<BottleFeedEntry> = emptyList()
) {
    val totalSleep: Duration
        get() = sleepEntries.fold(Duration.ZERO) { acc, e -> acc.plus(e.duration) }
    val totalFeed: Duration
        get() = feedEntries.fold(Duration.ZERO) { acc, e -> acc.plus(e.duration) }
    val totalDiapers: Int
        get() = diaperEntries.size
    val totalBottleMl: Int
        get() = bottleFeedEntries.sumOf { it.amountMl }
    val hasData: Boolean
        get() = sleepEntries.isNotEmpty() || diaperEntries.isNotEmpty() || activityEntries.isNotEmpty() || feedEntries.isNotEmpty() || bottleFeedEntries.isNotEmpty()
}

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val prefsRepository = PreferencesRepository(application)

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth

    private val _calendarData = MutableStateFlow<Map<LocalDate, CalendarDayData>>(emptyMap())
    val calendarData: StateFlow<Map<LocalDate, CalendarDayData>> = _calendarData

    private val _selectedDay = MutableStateFlow<CalendarDayData?>(null)
    val selectedDay: StateFlow<CalendarDayData?> = _selectedDay

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        loadMonth()
    }

    fun syncAndRefresh() {
        _isRefreshing.value = true
        viewModelScope.launch {
            SyncHelper.pullLatest()
            loadMonthInternal()
            _isRefreshing.value = false
        }
    }

    fun setMonth(yearMonth: YearMonth) {
        _currentMonth.value = yearMonth
        loadMonth()
    }

    fun previousMonth() {
        setMonth(_currentMonth.value.minusMonths(1))
    }

    fun nextMonth() {
        setMonth(_currentMonth.value.plusMonths(1))
    }

    fun selectDay(date: LocalDate) {
        _selectedDay.value = _calendarData.value[date] ?: CalendarDayData(date)
    }

    fun clearSelection() {
        _selectedDay.value = null
    }

    private fun loadMonth() {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch { loadMonthInternal() }
    }

    private suspend fun loadMonthInternal() {
        val uri = prefsRepository.fileUri ?: return
        val data = fileRepository.readAll(uri)
        val month = _currentMonth.value
        val dataMap = mutableMapOf<LocalDate, CalendarDayData>()

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            dataMap[date] = CalendarDayData(
                date = date,
                sleepEntries = data.sleepEntries.filter { it.date == date },
                diaperEntries = data.diaperEntries.filter { it.date == date },
                activityEntries = data.activityEntries.filter { it.date == date },
                feedEntries = data.feedEntries.filter { it.date == date },
                bottleFeedEntries = data.bottleFeedEntries.filter { it.date == date }
            )
        }

        _calendarData.value = dataMap

        // Update selected day if it's still in this month
        _selectedDay.value?.let { selected ->
            if (selected.date.month == month.month && selected.date.year == month.year) {
                _selectedDay.value = dataMap[selected.date]
            }
        }
    }
}
