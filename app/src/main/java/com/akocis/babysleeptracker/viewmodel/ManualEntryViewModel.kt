package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.ActivityType
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.FeedEntry
import com.akocis.babysleeptracker.model.FeedSide
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.repository.EntryParser
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.repository.SyncHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class EntryKind { SLEEP, DIAPER, ACTIVITY, FEED }

class ManualEntryViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val prefsRepository = PreferencesRepository(application)

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date

    private val _startTime = MutableStateFlow(LocalTime.of(8, 0))
    val startTime: StateFlow<LocalTime> = _startTime

    private val _endTime = MutableStateFlow(LocalTime.of(9, 0))
    val endTime: StateFlow<LocalTime> = _endTime

    private val _hasEndTime = MutableStateFlow(true)
    val hasEndTime: StateFlow<Boolean> = _hasEndTime

    private val _entryKind = MutableStateFlow(EntryKind.SLEEP)
    val entryKind: StateFlow<EntryKind> = _entryKind

    private val _diaperType = MutableStateFlow(DiaperType.PEE)
    val diaperType: StateFlow<DiaperType> = _diaperType

    private val _activityType = MutableStateFlow(ActivityType.STROLLER)
    val activityType: StateFlow<ActivityType> = _activityType

    private val _noteText = MutableStateFlow("")
    val noteText: StateFlow<String> = _noteText

    private val _feedSide = MutableStateFlow(FeedSide.LEFT)
    val feedSide: StateFlow<FeedSide> = _feedSide

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var editMode = false
    private var originalRawLine: String? = null

    val isEditMode: Boolean get() = editMode

    fun setDate(date: LocalDate) { _date.value = date }
    fun setStartTime(time: LocalTime) { _startTime.value = time }
    fun setEndTime(time: LocalTime) { _endTime.value = time }
    fun setHasEndTime(has: Boolean) { _hasEndTime.value = has }
    fun setEntryKind(kind: EntryKind) { _entryKind.value = kind }
    fun setDiaperType(type: DiaperType) { _diaperType.value = type }
    fun setActivityType(type: ActivityType) { _activityType.value = type }
    fun setNoteText(text: String) { _noteText.value = text }
    fun setFeedSide(side: FeedSide) { _feedSide.value = side }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun initForEdit(rawLine: String) {
        if (editMode) return
        editMode = true
        originalRawLine = rawLine
        when (val entry = EntryParser.parseLine(rawLine)) {
            is SleepEntry -> {
                _entryKind.value = EntryKind.SLEEP
                _date.value = entry.date
                _startTime.value = entry.startTime
                _hasEndTime.value = entry.endTime != null
                if (entry.endTime != null) {
                    _endTime.value = entry.endTime
                }
            }
            is DiaperEntry -> {
                _entryKind.value = EntryKind.DIAPER
                _date.value = entry.date
                _startTime.value = entry.time
                _diaperType.value = entry.type
            }
            is ActivityEntry -> {
                _entryKind.value = EntryKind.ACTIVITY
                _date.value = entry.date
                _startTime.value = entry.time
                _activityType.value = entry.type
                _noteText.value = entry.note ?: ""
            }
            is FeedEntry -> {
                _entryKind.value = EntryKind.FEED
                _date.value = entry.date
                _startTime.value = entry.startTime
                _feedSide.value = entry.side
                _hasEndTime.value = entry.endTime != null
                if (entry.endTime != null) {
                    _endTime.value = entry.endTime
                }
            }
        }
    }

    fun save() {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            try {
                val newLine = when (_entryKind.value) {
                    EntryKind.SLEEP -> {
                        val end = if (_hasEndTime.value) _endTime.value else null
                        EntryParser.formatSleepEntry(
                            SleepEntry(_date.value, _startTime.value, end)
                        )
                    }
                    EntryKind.DIAPER -> {
                        EntryParser.formatDiaperEntry(
                            DiaperEntry(_diaperType.value, _date.value, _startTime.value)
                        )
                    }
                    EntryKind.ACTIVITY -> {
                        val note = _noteText.value.takeIf { it.isNotBlank() }
                        EntryParser.formatActivityEntry(
                            ActivityEntry(_activityType.value, _date.value, _startTime.value, note)
                        )
                    }
                    EntryKind.FEED -> {
                        val end = if (_hasEndTime.value) _endTime.value else null
                        EntryParser.formatFeedEntry(
                            FeedEntry(_feedSide.value, _date.value, _startTime.value, end)
                        )
                    }
                }

                if (editMode && originalRawLine != null) {
                    fileRepository.updateEntry(uri, originalRawLine!!, newLine)
                } else {
                    when (_entryKind.value) {
                        EntryKind.SLEEP -> {
                            val end = if (_hasEndTime.value) _endTime.value else null
                            fileRepository.appendSleepEntry(
                                uri,
                                SleepEntry(_date.value, _startTime.value, end)
                            )
                        }
                        EntryKind.DIAPER -> {
                            fileRepository.appendDiaperEntry(
                                uri,
                                DiaperEntry(_diaperType.value, _date.value, _startTime.value)
                            )
                        }
                        EntryKind.ACTIVITY -> {
                            val note = _noteText.value.takeIf { it.isNotBlank() }
                            fileRepository.appendActivityEntry(
                                uri,
                                ActivityEntry(_activityType.value, _date.value, _startTime.value, note)
                            )
                        }
                        EntryKind.FEED -> {
                            val end = if (_hasEndTime.value) _endTime.value else null
                            fileRepository.appendFeedEntry(
                                uri,
                                FeedEntry(_feedSide.value, _date.value, _startTime.value, end)
                            )
                        }
                    }
                }
                _saved.value = true
                SyncHelper.notifyDataChanged()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save entry: ${e.message}"
            }
        }
    }

    fun resetSaved() { _saved.value = false }
}
