package com.akocis.babysleeptracker.model

import java.time.Duration
import java.time.LocalDate

data class DayStats(
    val date: LocalDate,
    val totalSleep: Duration,
    val sleepCount: Int,
    val peeCount: Int,
    val pooCount: Int,
    val peepooCount: Int,
    val daySleep: Duration = Duration.ZERO,
    val nightSleep: Duration = Duration.ZERO,
    val longestNap: Duration = Duration.ZERO,
    val shortestNap: Duration = Duration.ZERO,
    val feedCount: Int = 0,
    val totalFeedDuration: Duration = Duration.ZERO,
    val leftFeedDuration: Duration = Duration.ZERO,
    val rightFeedDuration: Duration = Duration.ZERO,
    val leftFeedCount: Int = 0,
    val rightFeedCount: Int = 0,
    val donorCount: Int = 0,
    val donorMl: Int = 0,
    val formulaCount: Int = 0,
    val formulaMl: Int = 0,
    val pumpedCount: Int = 0,
    val pumpedMl: Int = 0,
    val strollerCount: Int = 0,
    val bathCount: Int = 0,
    val noteCount: Int = 0,
    val label: String? = null,
    val timeSinceLastFeed: String? = null,
    val timeSinceLastBreastFeed: String? = null,
    val timeSinceLastBottleFeed: String? = null,
    val timeSinceLastBath: String? = null
) {
    val totalDiapers: Int get() = peeCount + pooCount + peepooCount
    val totalBottleFeeds: Int get() = donorCount + formulaCount + pumpedCount
    val totalBottleMl: Int get() = donorMl + formulaMl + pumpedMl
    val totalActivities: Int get() = strollerCount + bathCount + noteCount
}
