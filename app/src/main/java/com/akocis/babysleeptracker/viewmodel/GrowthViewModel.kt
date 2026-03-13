package com.akocis.babysleeptracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akocis.babysleeptracker.model.BabySex
import com.akocis.babysleeptracker.model.MeasurementEntry
import com.akocis.babysleeptracker.repository.EntryParser
import com.akocis.babysleeptracker.repository.FileRepository
import com.akocis.babysleeptracker.repository.PreferencesRepository
import com.akocis.babysleeptracker.repository.SyncHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class GrowthViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val prefsRepository = PreferencesRepository(application)

    private val _measurements = MutableStateFlow<List<MeasurementEntry>>(emptyList())
    val measurements: StateFlow<List<MeasurementEntry>> = _measurements

    private val _babySex = MutableStateFlow<BabySex?>(null)
    val babySex: StateFlow<BabySex?> = _babySex

    private val _babyBirthDate = MutableStateFlow<LocalDate?>(null)
    val babyBirthDate: StateFlow<LocalDate?> = _babyBirthDate

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val useMetric: Boolean get() = prefsRepository.useMetric

    init {
        loadData()
    }

    fun syncAndRefresh() {
        _isRefreshing.value = true
        loadData { _isRefreshing.value = false }
    }

    fun loadData(onComplete: (() -> Unit)? = null) {
        val uri = prefsRepository.fileUri ?: run { onComplete?.invoke(); return }
        viewModelScope.launch {
            SyncHelper.pullLatest()
            val data = fileRepository.readAll(uri)
            _measurements.value = data.measurementEntries.sortedBy { it.date }
            _babySex.value = data.babySex ?: prefsRepository.babySex?.let { BabySex.fromString(it) }
            _babyBirthDate.value = data.babyBirthDate ?: prefsRepository.babyBirthDate
            onComplete?.invoke()
        }
    }

    fun addMeasurement(entry: MeasurementEntry) {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            fileRepository.appendMeasurementEntry(uri, entry)
            SyncHelper.notifyDataChanged()
            loadData()
        }
    }

    fun updateMeasurement(old: MeasurementEntry, updated: MeasurementEntry) {
        val uri = prefsRepository.fileUri ?: return
        val id = old.id ?: return
        viewModelScope.launch {
            val newLine = EntryParser.formatMeasurementEntry(updated.copy(id = id))
            fileRepository.replaceById(uri, id, newLine)
            SyncHelper.notifyDataChanged()
            loadData()
        }
    }

    fun deleteMeasurement(entry: MeasurementEntry) {
        val uri = prefsRepository.fileUri ?: return
        viewModelScope.launch {
            val line = EntryParser.formatMeasurementEntry(entry)
            fileRepository.deleteEntry(uri, line)
            SyncHelper.notifyDataChanged()
            loadData()
        }
    }
}
