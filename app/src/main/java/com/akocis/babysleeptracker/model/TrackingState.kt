package com.akocis.babysleeptracker.model

import java.time.LocalDate
import java.time.LocalTime

sealed class TrackingState {
    data object Idle : TrackingState()
    data class Sleeping(
        val startDate: LocalDate,
        val startTime: LocalTime
    ) : TrackingState()
    data class Feeding(
        val side: FeedSide,
        val startDate: LocalDate,
        val startTime: LocalTime
    ) : TrackingState()
}
