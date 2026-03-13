package com.akocis.babysleeptracker.model

import java.time.LocalDate

data class MeasurementEntry(
    val date: LocalDate,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val headCm: Double? = null,
    val id: String? = null
)
