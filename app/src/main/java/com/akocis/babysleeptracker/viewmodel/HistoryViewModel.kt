package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.repository.EntryParser
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HistoryItem(
    val id: Int,
    val displayText: String,
    val rawLine: String,
    val sortKey: Long,
    val sleepEntry: SleepEntry? = null,
    val diaperEntry: DiaperEntry? = null,
    val activityEntry: ActivityEntry? = null
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val prefsRepository = PreferencesRepository(application)

    private val _entries = MutableStateFlow<List<HistoryItem>>(emptyList())
    val entries: StateFlow<List<HistoryItem>> = _entries

    init {
        loadEntries()
    }

    fun loadEntries() {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            val data = fileRepository.readAll(uri)
            val items = mutableListOf<HistoryItem>()
            var nextId = 0

            data.sleepEntries.forEach { entry ->
                val line = EntryParser.formatSleepEntry(entry)
                val sortKey = entry.date.toEpochDay() * 86400 +
                    entry.startTime.toSecondOfDay()
                val endText = entry.endTime?.toString() ?: "ongoing"
                items.add(
                    HistoryItem(
                        id = nextId++,
                        displayText = "Sleep: ${entry.date} ${entry.startTime} - $endText",
                        rawLine = line,
                        sortKey = sortKey,
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
                        displayText = "${entry.type.label}: ${entry.date} ${entry.time}",
                        rawLine = line,
                        sortKey = sortKey,
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
                        displayText = "${entry.type.label}: ${entry.date} ${entry.time}$noteText",
                        rawLine = line,
                        sortKey = sortKey,
                        activityEntry = entry
                    )
                )
            }

            _entries.value = items.sortedByDescending { it.sortKey }
        }
    }

    fun deleteEntry(item: HistoryItem) {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            fileRepository.deleteEntry(uri, item.rawLine)
            loadEntries()
        }
    }

    fun reAddEntry(rawLine: String) {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            when (val entry = EntryParser.parseLine(rawLine)) {
                is SleepEntry -> fileRepository.appendSleepEntry(uri, entry)
                is DiaperEntry -> fileRepository.appendDiaperEntry(uri, entry)
                is ActivityEntry -> fileRepository.appendActivityEntry(uri, entry)
            }
            loadEntries()
        }
    }
}
