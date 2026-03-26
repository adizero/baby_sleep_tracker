package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.ActivityType
import com.akocis.babysleeptracker.model.BottleFeedEntry
import com.akocis.babysleeptracker.model.BottleType
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.FeedEntry
import com.akocis.babysleeptracker.model.FeedSide
import com.akocis.babysleeptracker.model.MeasurementEntry
import com.akocis.babysleeptracker.model.NoiseType
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.model.WhiteNoiseEntry
import com.akocis.babysleeptracker.repository.EntryParser
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.repository.SyncHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class EntryKind { SLEEP, DIAPER, ACTIVITY, FEED, BOTTLE, NOISE, MEASURE }

class ManualEntryViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val prefsRepository = PreferencesRepository(application)

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date

    private val _startTime = MutableStateFlow(LocalTime.now().withSecond(0).withNano(0))
    val startTime: StateFlow<LocalTime> = _startTime

    private val _endTime = MutableStateFlow(LocalTime.now().withSecond(0).withNano(0).plusHours(1))
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

    private val _bottleType = MutableStateFlow(BottleType.DONOR)
    val bottleType: StateFlow<BottleType> = _bottleType

    private val _noiseType = MutableStateFlow(NoiseType.WHITE)
    val noiseType: StateFlow<NoiseType> = _noiseType

    private val _bottleAmountMl = MutableStateFlow(
        prefsRepository.bottlePresetMl.let { if (it > 0) it else 42 }
    )
    val bottleAmountMl: StateFlow<Int> = _bottleAmountMl

    private val _bottleUseOz = MutableStateFlow(prefsRepository.bottleUseOz)
    val bottleUseOz: StateFlow<Boolean> = _bottleUseOz

    private val _measureWeightText = MutableStateFlow("")
    val measureWeightText: StateFlow<String> = _measureWeightText

    private val _measureHeightText = MutableStateFlow("")
    val measureHeightText: StateFlow<String> = _measureHeightText

    private val _measureHeadText = MutableStateFlow("")
    val measureHeadText: StateFlow<String> = _measureHeadText

    private val _measureUseKg = MutableStateFlow(prefsRepository.useKg)
    val measureUseKg: StateFlow<Boolean> = _measureUseKg

    private val _measureUseCm = MutableStateFlow(prefsRepository.useCm)
    val measureUseCm: StateFlow<Boolean> = _measureUseCm

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var editMode = false
    private var originalRawLine: String? = null
    private var editEntryId: String? = null

    val isEditMode: Boolean get() = editMode

    fun setDate(date: LocalDate) { _date.value = date }
    fun setStartTime(time: LocalTime) {
        val hadEnd = _hasEndTime.value
        val oldStart = _startTime.value
        _startTime.value = time
        // Keep duration stable when adjusting start time with end time set
        if (hadEnd) {
            val duration = java.time.Duration.between(oldStart, _endTime.value)
            if (!duration.isNegative && !duration.isZero) {
                _endTime.value = time.plus(duration)
            }
        }
    }
    fun setEndTime(time: LocalTime) { _endTime.value = time }
    fun setHasEndTime(has: Boolean) {
        _hasEndTime.value = has
        if (has) {
            val defaultMinutes = when (_entryKind.value) {
                EntryKind.SLEEP -> 60L
                EntryKind.FEED -> 15L
                EntryKind.NOISE -> 30L
                else -> 30L
            }
            _endTime.value = _startTime.value.plusMinutes(defaultMinutes)
        }
    }
    fun setEntryKind(kind: EntryKind) { _entryKind.value = kind }
    fun setDiaperType(type: DiaperType) { _diaperType.value = type }
    fun setActivityType(type: ActivityType) { _activityType.value = type }
    fun setNoteText(text: String) { _noteText.value = text }
    fun setFeedSide(side: FeedSide) { _feedSide.value = side }
    fun setBottleType(type: BottleType) { _bottleType.value = type }
    fun setBottleAmountMl(ml: Int) { _bottleAmountMl.value = ml }
    fun setBottleUseOz(useOz: Boolean) {
        _bottleUseOz.value = useOz
    }
    fun adjustDuration(deltaMinutes: Int) {
        val newEnd = _endTime.value.plusMinutes(deltaMinutes.toLong())
        // Only allow if result is after start time
        if (newEnd.isAfter(_startTime.value)) {
            _endTime.value = newEnd
        }
    }
    fun setNoiseType(type: NoiseType) { _noiseType.value = type }
    fun setMeasureWeightText(text: String) { _measureWeightText.value = text }
    fun setMeasureHeightText(text: String) { _measureHeightText.value = text }
    fun setMeasureHeadText(text: String) { _measureHeadText.value = text }
    fun setMeasureUseKg(useKg: Boolean) {
        val oldUseKg = _measureUseKg.value
        if (oldUseKg == useKg) return
        _measureUseKg.value = useKg
        _measureWeightText.value.toDoubleOrNull()?.let { v ->
            _measureWeightText.value = if (useKg) {
                "%.3f".format(v / 2.20462) // lbs -> kg
            } else {
                "%.1f".format(v * 2.20462) // kg -> lbs
            }
        }
    }

    fun setMeasureUseCm(useCm: Boolean) {
        val oldUseCm = _measureUseCm.value
        if (oldUseCm == useCm) return
        _measureUseCm.value = useCm
        _measureHeightText.value.toDoubleOrNull()?.let { v ->
            _measureHeightText.value = if (useCm) {
                "%.1f".format(v * 2.54) // in -> cm
            } else {
                "%.1f".format(v / 2.54) // cm -> in
            }
        }
        _measureHeadText.value.toDoubleOrNull()?.let { v ->
            _measureHeadText.value = if (useCm) {
                "%.1f".format(v * 2.54) // in -> cm
            } else {
                "%.1f".format(v / 2.54) // cm -> in
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun initForEdit(rawLine: String) {
        if (editMode) return
        editMode = true
        originalRawLine = rawLine
        editEntryId = EntryParser.extractId(rawLine)
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
            is BottleFeedEntry -> {
                _entryKind.value = EntryKind.BOTTLE
                _date.value = entry.date
                _startTime.value = entry.time
                _bottleType.value = entry.type
                _bottleAmountMl.value = entry.amountMl
            }
            is WhiteNoiseEntry -> {
                _entryKind.value = EntryKind.NOISE
                _date.value = entry.date
                _startTime.value = entry.startTime
                _noiseType.value = entry.noiseType
                _hasEndTime.value = entry.endTime != null
                if (entry.endTime != null) {
                    _endTime.value = entry.endTime
                }
            }
            is MeasurementEntry -> {
                _entryKind.value = EntryKind.MEASURE
                _date.value = entry.date
                _startTime.value = entry.time ?: LocalTime.now()
                val useKg = prefsRepository.useKg
                val useCm = prefsRepository.useCm
                _measureUseKg.value = useKg
                _measureUseCm.value = useCm
                _measureWeightText.value = entry.weightKg?.let {
                    if (useKg) "%.3f".format(it) else "%.1f".format(it * 2.20462)
                } ?: ""
                _measureHeightText.value = entry.heightCm?.let {
                    if (useCm) "%.1f".format(it) else "%.1f".format(it / 2.54)
                } ?: ""
                _measureHeadText.value = entry.headCm?.let {
                    if (useCm) "%.1f".format(it) else "%.1f".format(it / 2.54)
                } ?: ""
            }
        }
    }

    fun save() {
        val uri = prefsRepository.fileUri ?: return

        // Validate bottle amount
        if (_entryKind.value == EntryKind.BOTTLE && _bottleAmountMl.value <= 0) {
            _errorMessage.value = "Amount must be greater than 0"
            return
        }

        // Validate measurement has at least one value
        if (_entryKind.value == EntryKind.MEASURE) {
            val w = _measureWeightText.value.toDoubleOrNull()
            val h = _measureHeightText.value.toDoubleOrNull()
            val c = _measureHeadText.value.toDoubleOrNull()
            if (w == null && h == null && c == null) {
                _errorMessage.value = "Enter at least one measurement"
                return
            }
        }

        // Validate end time > start time when end time is set
        if (_hasEndTime.value && (_entryKind.value == EntryKind.SLEEP || _entryKind.value == EntryKind.FEED || _entryKind.value == EntryKind.NOISE)) {
            if (_endTime.value <= _startTime.value) {
                _errorMessage.value = "End time must be after start time"
                return
            }
        }

        // Validate timestamps are not in the future and not before birth (skip for NOTE)
        val isNote = _entryKind.value == EntryKind.ACTIVITY && _activityType.value == ActivityType.NOTE
        if (!isNote) {
            val now = LocalDateTime.now()
            val startDateTime = _date.value.atTime(_startTime.value)
            if (startDateTime.isAfter(now)) {
                _errorMessage.value = "Start time cannot be in the future"
                return
            }
            if (_hasEndTime.value) {
                val endDateTime = _date.value.atTime(_endTime.value)
                if (endDateTime.isAfter(now)) {
                    _errorMessage.value = "End time cannot be in the future"
                    return
                }
            }
            val birthDate = prefsRepository.babyBirthDate
            if (birthDate != null && _date.value.isBefore(birthDate)) {
                _errorMessage.value = "Date cannot be before baby's birth date ($birthDate)"
                return
            }
        }

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
                    EntryKind.BOTTLE -> {
                        EntryParser.formatBottleFeedEntry(
                            BottleFeedEntry(_bottleType.value, _date.value, _startTime.value, _bottleAmountMl.value)
                        )
                    }
                    EntryKind.NOISE -> {
                        val end = if (_hasEndTime.value) _endTime.value else null
                        EntryParser.formatWhiteNoiseEntry(
                            WhiteNoiseEntry(_noiseType.value, _date.value, _startTime.value, end)
                        )
                    }
                    EntryKind.MEASURE -> {
                        val useKg = _measureUseKg.value
                        val useCm = _measureUseCm.value
                        val w = _measureWeightText.value.toDoubleOrNull()
                        val h = _measureHeightText.value.toDoubleOrNull()
                        val c = _measureHeadText.value.toDoubleOrNull()
                        val weightKg = w?.let { if (useKg) it else it / 2.20462 }
                        val heightCm = h?.let { if (useCm) it else it * 2.54 }
                        val headCm = c?.let { if (useCm) it else it * 2.54 }
                        EntryParser.formatMeasurementEntry(
                            MeasurementEntry(_date.value, weightKg, heightCm, headCm, time = _startTime.value)
                        )
                    }
                }

                if (editMode && editEntryId != null) {
                    fileRepository.replaceById(uri, editEntryId!!, newLine)
                } else if (editMode && originalRawLine != null) {
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
                        EntryKind.BOTTLE -> {
                            fileRepository.appendBottleFeedEntry(
                                uri,
                                BottleFeedEntry(_bottleType.value, _date.value, _startTime.value, _bottleAmountMl.value)
                            )
                        }
                        EntryKind.NOISE -> {
                            val end = if (_hasEndTime.value) _endTime.value else null
                            fileRepository.appendWhiteNoiseEntry(
                                uri,
                                WhiteNoiseEntry(_noiseType.value, _date.value, _startTime.value, end)
                            )
                        }
                        EntryKind.MEASURE -> {
                            val useKg = _measureUseKg.value
                            val useCm = _measureUseCm.value
                            val w = _measureWeightText.value.toDoubleOrNull()
                            val h = _measureHeightText.value.toDoubleOrNull()
                            val c = _measureHeadText.value.toDoubleOrNull()
                            val weightKg = w?.let { if (useKg) it else it / 2.20462 }
                            val heightCm = h?.let { if (useCm) it else it * 2.54 }
                            val headCm = c?.let { if (useCm) it else it * 2.54 }
                            fileRepository.appendMeasurementEntry(
                                uri,
                                MeasurementEntry(_date.value, weightKg, heightCm, headCm, time = _startTime.value)
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
