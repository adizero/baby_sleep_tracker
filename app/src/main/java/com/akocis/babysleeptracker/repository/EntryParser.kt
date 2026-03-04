package com.akocis.babysleeptracker.repository

import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.ActivityType
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.util.DateTimeUtil
import java.time.LocalDate
import java.time.LocalTime

data class ParsedData(
    val sleepEntries: List<SleepEntry> = emptyList(),
    val diaperEntries: List<DiaperEntry> = emptyList(),
    val activityEntries: List<ActivityEntry> = emptyList(),
    val babyName: String? = null,
    val babyBirthDate: LocalDate? = null
)

object EntryParser {

    private val SLEEP_REGEX = Regex(
        """^SLEEP\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})$"""
    )
    private val SLEEP_ONGOING_REGEX = Regex(
        """^SLEEP\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s*-\s*$"""
    )
    private val DIAPER_REGEX = Regex(
        """^(PEE|POO|PEEPOO)\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})$"""
    )
    private val ACTIVITY_REGEX = Regex(
        """^(STROLLER|BATH)\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})$"""
    )
    private val NOTE_REGEX = Regex(
        """^NOTE\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s+(.+)$"""
    )
    private val BABY_REGEX = Regex(
        """^BABY\s+(.+?)\s+(\d{4}-\d{2}-\d{2})$"""
    )

    fun parseLine(line: String): Any? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null

        SLEEP_REGEX.matchEntire(trimmed)?.let { match ->
            val date = LocalDate.parse(match.groupValues[1], DateTimeUtil.DATE_FORMAT)
            val start = LocalTime.parse(match.groupValues[2], DateTimeUtil.TIME_FORMAT)
            val end = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            return SleepEntry(date, start, end)
        }

        SLEEP_ONGOING_REGEX.matchEntire(trimmed)?.let { match ->
            val date = LocalDate.parse(match.groupValues[1], DateTimeUtil.DATE_FORMAT)
            val start = LocalTime.parse(match.groupValues[2], DateTimeUtil.TIME_FORMAT)
            return SleepEntry(date, start, null)
        }

        DIAPER_REGEX.matchEntire(trimmed)?.let { match ->
            val type = DiaperType.fromString(match.groupValues[1]) ?: return null
            val date = LocalDate.parse(match.groupValues[2], DateTimeUtil.DATE_FORMAT)
            val time = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            return DiaperEntry(type, date, time)
        }

        NOTE_REGEX.matchEntire(trimmed)?.let { match ->
            val date = LocalDate.parse(match.groupValues[1], DateTimeUtil.DATE_FORMAT)
            val time = LocalTime.parse(match.groupValues[2], DateTimeUtil.TIME_FORMAT)
            val text = match.groupValues[3]
            return ActivityEntry(ActivityType.NOTE, date, time, text)
        }

        ACTIVITY_REGEX.matchEntire(trimmed)?.let { match ->
            val type = ActivityType.fromString(match.groupValues[1]) ?: return null
            val date = LocalDate.parse(match.groupValues[2], DateTimeUtil.DATE_FORMAT)
            val time = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            return ActivityEntry(type, date, time)
        }

        return null
    }

    fun parseBabyInfo(content: String): Pair<String?, LocalDate?> {
        content.lines().forEach { line ->
            BABY_REGEX.matchEntire(line.trim())?.let { match ->
                val name = match.groupValues[1]
                val date = LocalDate.parse(match.groupValues[2], DateTimeUtil.DATE_FORMAT)
                return name to date
            }
        }
        return null to null
    }

    fun parseAll(content: String): ParsedData {
        val sleepEntries = mutableListOf<SleepEntry>()
        val diaperEntries = mutableListOf<DiaperEntry>()
        val activityEntries = mutableListOf<ActivityEntry>()
        var babyName: String? = null
        var babyBirthDate: LocalDate? = null

        content.lines().forEach { line ->
            val trimmed = line.trim()
            BABY_REGEX.matchEntire(trimmed)?.let { match ->
                babyName = match.groupValues[1]
                babyBirthDate = LocalDate.parse(match.groupValues[2], DateTimeUtil.DATE_FORMAT)
                return@forEach
            }
            when (val entry = parseLine(line)) {
                is SleepEntry -> sleepEntries.add(entry)
                is DiaperEntry -> diaperEntries.add(entry)
                is ActivityEntry -> activityEntries.add(entry)
            }
        }

        return ParsedData(sleepEntries, diaperEntries, activityEntries, babyName, babyBirthDate)
    }

    fun formatSleepEntry(entry: SleepEntry): String {
        val date = entry.date.format(DateTimeUtil.DATE_FORMAT)
        val start = entry.startTime.format(DateTimeUtil.TIME_FORMAT)
        return if (entry.endTime != null) {
            val end = entry.endTime.format(DateTimeUtil.TIME_FORMAT)
            "SLEEP $date $start - $end"
        } else {
            "SLEEP $date $start -"
        }
    }

    fun formatDiaperEntry(entry: DiaperEntry): String {
        val date = entry.date.format(DateTimeUtil.DATE_FORMAT)
        val time = entry.time.format(DateTimeUtil.TIME_FORMAT)
        return "${entry.type.name} $date $time"
    }

    fun formatActivityEntry(entry: ActivityEntry): String {
        val date = entry.date.format(DateTimeUtil.DATE_FORMAT)
        val time = entry.time.format(DateTimeUtil.TIME_FORMAT)
        return if (entry.note != null) {
            "${entry.type.name} $date $time ${entry.note}"
        } else {
            "${entry.type.name} $date $time"
        }
    }

    fun formatBabyInfo(name: String, birthDate: LocalDate): String {
        return "BABY $name ${birthDate.format(DateTimeUtil.DATE_FORMAT)}"
    }
}
