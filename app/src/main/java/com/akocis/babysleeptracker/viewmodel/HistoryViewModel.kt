package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.BottleFeedEntry
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.FeedEntry
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

data class HistoryItem(
    val id: Int,
    val displayText: String,
    val durationText: String? = null,
    val rawLine: String,
    val sortKey: Long,
    val date: LocalDate,
    val hour: Int,
    val sleepEntry: SleepEntry? = null,
    val diaperEntry: DiaperEntry? = null,
    val activityEntry: ActivityEntry? = null,
    val feedEntry: FeedEntry? = null,
    val bottleFeedEntry: BottleFeedEntry? = null,
    val whiteNoiseEntry: WhiteNoiseEntry? = null,
    val measurementEntry: MeasurementEntry? = null
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val prefsRepository = PreferencesRepository(application)

    private val _entries = MutableStateFlow<List<HistoryItem>>(emptyList())
    val entries: StateFlow<List<HistoryItem>> = _entries

    private val _showDuration = MutableStateFlow(false)
    val showDuration: StateFlow<Boolean> = _showDuration

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val dayStartHour: Int get() = prefsRepository.dayStartHour
    val dayEndHour: Int get() = prefsRepository.dayEndHour

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }

    init {
        loadEntries()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun toggleShowDuration() {
        _showDuration.value = !_showDuration.value
    }

    fun syncAndRefresh() {
        _isRefreshing.value = true
        loadEntries { _isRefreshing.value = false }
    }

    fun loadEntries(onComplete: (() -> Unit)? = null) {
        val uri = prefsRepository.fileUri ?: run { onComplete?.invoke(); return }
        viewModelScope.launch {
            SyncHelper.pullLatest()
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
                        displayText = "${entry.type.label}  ${entry.time.format(TIME_FMT)}  ${entry.amountMl}ml",
                        rawLine = line,
                        sortKey = sortKey,
                        date = entry.date,
                        hour = entry.time.hour,
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
                        whiteNoiseEntry = entry
                    )
                )
            }

            val useMetric = prefsRepository.useMetric
            data.measurementEntries.forEach { entry ->
                val line = EntryParser.formatMeasurementEntry(entry)
                val sortKey = entry.date.toEpochDay() * 86400 + 43200L // noon
                val parts = mutableListOf<String>()
                entry.weightKg?.let {
                    parts.add(if (useMetric) "${"%.2f".format(it)} kg" else "${"%.1f".format(it * 2.20462)} lbs")
                }
                entry.heightCm?.let {
                    parts.add(if (useMetric) "${"%.1f".format(it)} cm" else "${"%.1f".format(it / 2.54)} in")
                }
                entry.headCm?.let {
                    parts.add("hc " + if (useMetric) "${"%.1f".format(it)} cm" else "${"%.1f".format(it / 2.54)} in")
                }
                items.add(
                    HistoryItem(
                        id = nextId++,
                        displayText = "Measure  ${parts.joinToString("  ")}",
                        rawLine = line,
                        sortKey = sortKey,
                        date = entry.date,
                        hour = 12,
                        measurementEntry = entry
                    )
                )
            }

            _entries.value = items.sortedByDescending { it.sortKey }
            onComplete?.invoke()
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
