package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.BottleFeedEntry
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.FeedEntry
import com.akocis.babysleeptracker.model.HighContrastEntry
import com.akocis.babysleeptracker.model.MeasurementEntry
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.model.WhiteNoiseEntry
import com.akocis.babysleeptracker.repository.EntryParser
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.repository.SyncHelper
import com.akocis.babysleeptracker.util.DateTimeUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class HistoryFilter(val label: String) {
    ALL("All"),
    SLEEP("Sleep"),
    FEED("Feed"),
    BOTTLE("Bottle"),
    DIAPER("Diaper"),
    ACTIVITY("Activity"),
    NOISE("Noise"),
    HC("HC"),
    MEASURE("Growth")
}

data class HistoryItem(
    val id: Int,
    val displayText: String,
    val durationText: String? = null,
    val rawLine: String,
    val sortKey: Long,
    val date: LocalDate,
    val hour: Int,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val sleepEntry: SleepEntry? = null,
    val diaperEntry: DiaperEntry? = null,
    val activityEntry: ActivityEntry? = null,
    val feedEntry: FeedEntry? = null,
    val bottleFeedEntry: BottleFeedEntry? = null,
    val whiteNoiseEntry: WhiteNoiseEntry? = null,
    val highContrastEntry: HighContrastEntry? = null,
    val measurementEntry: MeasurementEntry? = null
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val prefsRepository = PreferencesRepository(application)

    private val _allEntries = MutableStateFlow<List<HistoryItem>>(emptyList())

    private val _activeFilter = MutableStateFlow(HistoryFilter.ALL)
    val activeFilter: StateFlow<HistoryFilter> = _activeFilter

    val entries: StateFlow<List<HistoryItem>> get() = _filteredEntries
    private val _filteredEntries = MutableStateFlow<List<HistoryItem>>(emptyList())

    private val _showDuration = MutableStateFlow(false)
    val showDuration: StateFlow<Boolean> = _showDuration

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val dayStartHour: Int get() = prefsRepository.dayStartHour
    val dayEndHour: Int get() = prefsRepository.dayEndHour

    private fun formatBottleAmount(ml: Int): String {
        return if (prefsRepository.bottleUseOz) "${"%.1f".format(ml / 29.5735)}oz" else "${ml}ml"
    }

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }

    init {
        loadEntries()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun setFilter(filter: HistoryFilter) {
        _activeFilter.value = filter
        applyFilter()
    }

    private fun applyFilter() {
        val filter = _activeFilter.value
        _filteredEntries.value = if (filter == HistoryFilter.ALL) {
            _allEntries.value
        } else {
            _allEntries.value.filter { it.filter == filter }
        }
    }

    fun toggleShowDuration() {
        _showDuration.value = !_showDuration.value
    }

    fun syncAndRefresh() {
        _isRefreshing.value = true
        viewModelScope.launch {
            SyncHelper.pullLatest()
            loadEntries(onComplete = { _isRefreshing.value = false }, sync = false)
        }
    }

    fun loadEntries(onComplete: (() -> Unit)? = null, sync: Boolean = true) {
        val uri = prefsRepository.fileUri ?: run { _isLoading.value = false; onComplete?.invoke(); return }
        viewModelScope.launch {
            val data = fileRepository.readAll(uri)
            val items = mutableListOf<HistoryItem>()
            var nextId = 0

            data.sleepEntries.forEach { entry ->
                val line = EntryParser.formatSleepEntry(entry)
                val sortKey = entry.date.toEpochDay() * 86400 +
                    entry.startTime.toSecondOfDay()
                val endText = entry.endTime?.format(TIME_FMT) ?: "ongoing"
                val startText = entry.startTime.format(TIME_FMT)
                items.add(
                    HistoryItem(
                        id = nextId++,
                        displayText = "Sleep  $startText - $endText",
                        durationText = "Sleep  $startText (${DateTimeUtil.formatDuration(entry.duration)})",
                        rawLine = line,
                        sortKey = sortKey,
                        date = entry.date,
                        hour = entry.startTime.hour,
                        filter = HistoryFilter.SLEEP,
                        sleepEntry = entry
                    )
                )
            }

            data.diaperEntries.forEach { entry ->
                val line = EntryParser.formatDiaperEntry(entry)
                val sortKey = entry.date.toEpochDay() * 86400 +
                    entry.time.toSecondOfDay()
                items.add(
                    HistoryItem(
                        id = nextId++,
                        displayText = "${entry.type.label}  ${entry.time.format(TIME_FMT)}",
                        rawLine = line,
                        sortKey = sortKey,
                        date = entry.date,
                        hour = entry.time.hour,
                        filter = HistoryFilter.DIAPER,
                        diaperEntry = entry
                    )
                )
            }

            data.activityEntries.forEach { entry ->
                val line = EntryParser.formatActivityEntry(entry)
                val sortKey = entry.date.toEpochDay() * 86400 +
                    entry.time.toSecondOfDay()
                val noteText = if (entry.note != null) " - ${entry.note}" else ""
                items.add(
                    HistoryItem(
                        id = nextId++,
                        displayText = "${entry.type.label}  ${entry.time.format(TIME_FMT)}$noteText",
                        rawLine = line,
                        sortKey = sortKey,
                        date = entry.date,
                        hour = entry.time.hour,
                        filter = HistoryFilter.ACTIVITY,
                        activityEntry = entry
                    )
                )
            }

            data.feedEntries.forEach { entry ->
                val line = EntryParser.formatFeedEntry(entry)
                val sortKey = entry.date.toEpochDay() * 86400 +
                    entry.startTime.toSecondOfDay()
                val endText = entry.endTime?.format(TIME_FMT) ?: "ongoing"
                val startText = entry.startTime.format(TIME_FMT)
                items.add(
                    HistoryItem(
                        id = nextId++,
                        displayText = "Feed (${entry.side.label})  $startText - $endText",
                        durationText = "Feed (${entry.side.label})  $startText (${DateTimeUtil.formatDuration(entry.duration)})",
                        rawLine = line,
                        sortKey = sortKey,
                        date = entry.date,
                        hour = entry.startTime.hour,
                        filter = HistoryFilter.FEED,
                        feedEntry = entry
                    )
                )
            }

            data.bottleFeedEntries.forEach { entry ->
                val line = EntryParser.formatBottleFeedEntry(entry)
                val sortKey = entry.date.toEpochDay() * 86400 +
                    entry.time.toSecondOfDay()
                items.add(
                    HistoryItem(
                        id = nextId++,
                        displayText = "${entry.type.label}  ${entry.time.format(TIME_FMT)}  ${formatBottleAmount(entry.amountMl)}",
                        rawLine = line,
                        sortKey = sortKey,
                        date = entry.date,
                        hour = entry.time.hour,
                        filter = HistoryFilter.BOTTLE,
                        bottleFeedEntry = entry
                    )
                )
            }

            data.whiteNoiseEntries.forEach { entry ->
                val line = EntryParser.formatWhiteNoiseEntry(entry)
                val sortKey = entry.date.toEpochDay() * 86400 +
                    entry.startTime.toSecondOfDay()
                val endText = entry.endTime?.format(TIME_FMT) ?: "ongoing"
                val startText = entry.startTime.format(TIME_FMT)
                items.add(
                    HistoryItem(
                        id = nextId++,
                        displayText = "${entry.noiseType.label} Noise  $startText - $endText",
                        durationText = "${entry.noiseType.label} Noise  $startText (${DateTimeUtil.formatDuration(entry.duration)})",
                        rawLine = line,
                        sortKey = sortKey,
                        date = entry.date,
                        hour = entry.startTime.hour,
                        filter = HistoryFilter.NOISE,
                        whiteNoiseEntry = entry
                    )
                )
            }

            data.highContrastEntries.forEach { entry ->
                val line = EntryParser.formatHighContrastEntry(entry)
                val sortKey = entry.date.toEpochDay() * 86400 +
                    entry.startTime.toSecondOfDay()
                val endText = entry.endTime?.format(TIME_FMT) ?: "ongoing"
                val startText = entry.startTime.format(TIME_FMT)
                val colorsText = if (entry.colors.isNotEmpty()) "  ${entry.colors}" else ""
                items.add(
                    HistoryItem(
                        id = nextId++,
                        displayText = "HC  $startText - $endText$colorsText",
                        durationText = "HC  $startText (${DateTimeUtil.formatDuration(entry.duration)})$colorsText",
                        rawLine = line,
                        sortKey = sortKey,
                        date = entry.date,
                        hour = entry.startTime.hour,
                        filter = HistoryFilter.HC,
                        highContrastEntry = entry
                    )
                )
            }

            val useKg = prefsRepository.useKg
            val useCm = prefsRepository.useCm
            data.measurementEntries.forEach { entry ->
                val line = EntryParser.formatMeasurementEntry(entry)
                val timeSeconds = entry.time?.toSecondOfDay()?.toLong() ?: 43200L
                val sortKey = entry.date.toEpochDay() * 86400 + timeSeconds
                val timeText = entry.time?.format(TIME_FMT)?.let { "$it  " } ?: ""
                val parts = mutableListOf<String>()
                entry.weightKg?.let {
                    parts.add(if (useKg) "${"%.3f".format(it)} kg" else "${"%.1f".format(it * 2.20462)} lbs")
                }
                entry.heightCm?.let {
                    parts.add(if (useCm) "${"%.1f".format(it)} cm" else "${"%.1f".format(it / 2.54)} in")
                }
                entry.headCm?.let {
                    parts.add("hc " + if (useCm) "${"%.1f".format(it)} cm" else "${"%.1f".format(it / 2.54)} in")
                }
                items.add(
                    HistoryItem(
                        id = nextId++,
                        displayText = "Measure  $timeText${parts.joinToString("  ")}",
                        rawLine = line,
                        sortKey = sortKey,
                        date = entry.date,
                        hour = entry.time?.hour ?: 12,
                        filter = HistoryFilter.MEASURE,
                        measurementEntry = entry
                    )
                )
            }

            _allEntries.value = items.sortedByDescending { it.sortKey }
            applyFilter()
            _isLoading.value = false
            onComplete?.invoke()
            if (sync) {
                SyncHelper.pullLatest()
                loadEntries(sync = false)
            }
        }
    }

    fun deleteEntry(item: HistoryItem) {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            try {
                fileRepository.deleteEntry(uri, item.rawLine)
                loadEntries()
                SyncHelper.notifyDataChanged()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete entry: ${e.message}"
            }
        }
    }

    fun reAddEntry(rawLine: String) {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            try {
                val entry = EntryParser.parseLine(rawLine)
                // Remove the DEL tombstone if the entry has an ID
                val entryId = when (entry) {
                    is SleepEntry -> entry.id
                    is DiaperEntry -> entry.id
                    is ActivityEntry -> entry.id
                    is FeedEntry -> entry.id
                    is BottleFeedEntry -> entry.id
                    is WhiteNoiseEntry -> entry.id
                    is HighContrastEntry -> entry.id
                    is MeasurementEntry -> entry.id
                    else -> null
                }
                if (entryId != null) {
                    fileRepository.removeDeletionTombstone(uri, entryId)
                }
                when (entry) {
                    is SleepEntry -> fileRepository.appendSleepEntry(uri, entry)
                    is DiaperEntry -> fileRepository.appendDiaperEntry(uri, entry)
                    is ActivityEntry -> fileRepository.appendActivityEntry(uri, entry)
                    is FeedEntry -> fileRepository.appendFeedEntry(uri, entry)
                    is BottleFeedEntry -> fileRepository.appendBottleFeedEntry(uri, entry)
                    is WhiteNoiseEntry -> fileRepository.appendWhiteNoiseEntry(uri, entry)
                    is HighContrastEntry -> fileRepository.appendHighContrastEntry(uri, entry)
                    is MeasurementEntry -> fileRepository.appendMeasurementEntry(uri, entry)
                }
                loadEntries()
                SyncHelper.notifyDataChanged()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to restore entry: ${e.message}"
            }
        }
    }
}
