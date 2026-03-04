package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.repository.EntryParser
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class ManualEntryViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val prefsRepository = PreferencesRepository(application)

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date

    private val _startTime = MutableStateFlow(LocalTime.of(8, 0))
    val startTime: StateFlow<LocalTime> = _startTime

    private val _endTime = MutableStateFlow(LocalTime.of(9, 0))
    val endTime: StateFlow<LocalTime> = _endTime

    private val _isSleepEntry = MutableStateFlow(true)
    val isSleepEntry: StateFlow<Boolean> = _isSleepEntry

    private val _diaperType = MutableStateFlow(DiaperType.PEE)
    val diaperType: StateFlow<DiaperType> = _diaperType

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    private var editMode = false
    private var originalRawLine: String? = null

    val isEditMode: Boolean get() = editMode

    fun setDate(date: LocalDate) { _date.value = date }
    fun setStartTime(time: LocalTime) { _startTime.value = time }
    fun setEndTime(time: LocalTime) { _endTime.value = time }
    fun setIsSleepEntry(isSleep: Boolean) { _isSleepEntry.value = isSleep }
    fun setDiaperType(type: DiaperType) { _diaperType.value = type }

    fun initForEdit(rawLine: String) {
        if (editMode) return
        editMode = true
        originalRawLine = rawLine
        when (val entry = EntryParser.parseLine(rawLine)) {
            is SleepEntry -> {
                _isSleepEntry.value = true
                _date.value = entry.date
                _startTime.value = entry.startTime
                _endTime.value = entry.endTime
            }
            is DiaperEntry -> {
                _isSleepEntry.value = false
                _date.value = entry.date
                _startTime.value = entry.time
                _diaperType.value = entry.type
            }
        }
    }

    fun save() {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            val newLine = if (_isSleepEntry.value) {
                EntryParser.formatSleepEntry(
                    SleepEntry(_date.value, _startTime.value, _endTime.value)
                )
            } else {
                EntryParser.formatDiaperEntry(
                    DiaperEntry(_diaperType.value, _date.value, _startTime.value)
                )
            }

            if (editMode && originalRawLine != null) {
                fileRepository.updateEntry(uri, originalRawLine!!, newLine)
            } else {
                if (_isSleepEntry.value) {
                    fileRepository.appendSleepEntry(
                        uri,
                        SleepEntry(_date.value, _startTime.value, _endTime.value)
                    )
                } else {
                    fileRepository.appendDiaperEntry(
                        uri,
                        DiaperEntry(_diaperType.value, _date.value, _startTime.value)
                    )
                }
            }
            _saved.value = true
        }
    }

    fun resetSaved() { _saved.value = false }
}
