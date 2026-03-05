package com.akocis.babysleeptracker.model

import java.time.LocalDate
import java.time.LocalTime

data class DiaperEntry(
    val type: DiaperType,
    val date: LocalDate,
    val time: LocalTime,
    val id: String? = null
)
