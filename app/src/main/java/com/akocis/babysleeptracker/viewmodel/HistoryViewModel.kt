package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val diaperEntry: DiaperEntry? = null
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
            val (sleepEntries, diaperEntries) = fileRepository.readAll(uri)
            val items = mutableListOf<HistoryItem>()
            var nextId = 0

            sleepEntries.forEach { entry ->
                val line = EntryParser.formatSleepEntry(entry)
                val sortKey = entry.date.toEpochDay() * 86400 +
                    entry.startTime.toSecondOfDay()
                items.add(
                    HistoryItem(
                        id = nextId++,
                        displayText = "Sleep: ${entry.date} ${entry.startTime} - ${entry.endTime}",
                        rawLine = line,
                        sortKey = sortKey,
                        sleepEntry = entry
                    )
                )
            }

            diaperEntries.forEach { entry ->
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
}
