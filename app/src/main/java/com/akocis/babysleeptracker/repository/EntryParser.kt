package com.akocis.babysleeptracker.repository

import com.akocis.babysleeptracker.model.ActivityEntry
import com.akocis.babysleeptracker.model.ActivityType
import com.akocis.babysleeptracker.model.BottleFeedEntry
import com.akocis.babysleeptracker.model.BottleType
import com.akocis.babysleeptracker.model.DiaperEntry
import com.akocis.babysleeptracker.model.DiaperType
import com.akocis.babysleeptracker.model.FeedEntry
import com.akocis.babysleeptracker.model.FeedSide
import com.akocis.babysleeptracker.model.NoiseType
import com.akocis.babysleeptracker.model.SleepEntry
import com.akocis.babysleeptracker.model.WhiteNoiseEntry
import com.akocis.babysleeptracker.util.DateTimeUtil
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime

data class ParsedData(
    val sleepEntries: List<SleepEntry> = emptyList(),
    val diaperEntries: List<DiaperEntry> = emptyList(),
    val activityEntries: List<ActivityEntry> = emptyList(),
    val feedEntries: List<FeedEntry> = emptyList(),
    val bottleFeedEntries: List<BottleFeedEntry> = emptyList(),
    val whiteNoiseEntries: List<WhiteNoiseEntry> = emptyList(),
    val babyName: String? = null,
    val babyBirthDate: LocalDate? = null,
    val deletedIds: Set<String> = emptySet()
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
    private val FEED_REGEX = Regex(
        """^(FEEDL|FEEDR)\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})$"""
    )
    private val FEED_ONGOING_REGEX = Regex(
        """^(FEEDL|FEEDR)\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s*-\s*$"""
    )
    private val BOTTLE_FEED_REGEX = Regex(
        """^(DONOR|FORMULA|PUMPED)\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s+(\d+)ml$"""
    )
    private val NOISE_REGEX = Regex(
        """^NOISE\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})\s+(\w+)$"""
    )
    private val NOISE_ONGOING_REGEX = Regex(
        """^NOISE\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s*-\s+(\w+)$"""
    )

    val ID_PREFIX_REGEX = Regex("""^#([0-9a-f]{8})\s+""")
    val DEL_REGEX = Regex("""^DEL\s+#([0-9a-f]{8})\s*$""")

    private val random = SecureRandom()

    fun generateId(): String {
        val bytes = ByteArray(4)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun extractId(line: String): String? {
        return ID_PREFIX_REGEX.find(line.trim())?.groupValues?.get(1)
    }

    fun stripId(line: String): String {
        return line.trim().replace(ID_PREFIX_REGEX, "")
    }

    fun formatDeletion(id: String): String = "DEL #$id"

    fun parseLine(line: String): Any? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null

        // Strip ID prefix if present
        val id = extractId(trimmed)
        val content = stripId(trimmed)

        SLEEP_REGEX.matchEntire(content)?.let { match ->
            val date = LocalDate.parse(match.groupValues[1], DateTimeUtil.DATE_FORMAT)
            val start = LocalTime.parse(match.groupValues[2], DateTimeUtil.TIME_FORMAT)
            val end = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            return SleepEntry(date, start, end, id)
        }

        SLEEP_ONGOING_REGEX.matchEntire(content)?.let { match ->
            val date = LocalDate.parse(match.groupValues[1], DateTimeUtil.DATE_FORMAT)
            val start = LocalTime.parse(match.groupValues[2], DateTimeUtil.TIME_FORMAT)
            return SleepEntry(date, start, null, id)
        }

        FEED_REGEX.matchEntire(content)?.let { match ->
            val side = FeedSide.fromString(match.groupValues[1]) ?: return null
            val date = LocalDate.parse(match.groupValues[2], DateTimeUtil.DATE_FORMAT)
            val start = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            val end = LocalTime.parse(match.groupValues[4], DateTimeUtil.TIME_FORMAT)
            return FeedEntry(side, date, start, end, id)
        }

        FEED_ONGOING_REGEX.matchEntire(content)?.let { match ->
            val side = FeedSide.fromString(match.groupValues[1]) ?: return null
            val date = LocalDate.parse(match.groupValues[2], DateTimeUtil.DATE_FORMAT)
            val start = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            return FeedEntry(side, date, start, null, id)
        }

        DIAPER_REGEX.matchEntire(content)?.let { match ->
            val type = DiaperType.fromString(match.groupValues[1]) ?: return null
            val date = LocalDate.parse(match.groupValues[2], DateTimeUtil.DATE_FORMAT)
            val time = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            return DiaperEntry(type, date, time, id)
        }

        NOTE_REGEX.matchEntire(content)?.let { match ->
            val date = LocalDate.parse(match.groupValues[1], DateTimeUtil.DATE_FORMAT)
            val time = LocalTime.parse(match.groupValues[2], DateTimeUtil.TIME_FORMAT)
            val text = match.groupValues[3]
            return ActivityEntry(ActivityType.NOTE, date, time, text, id)
        }

        ACTIVITY_REGEX.matchEntire(content)?.let { match ->
            val type = ActivityType.fromString(match.groupValues[1]) ?: return null
            val date = LocalDate.parse(match.groupValues[2], DateTimeUtil.DATE_FORMAT)
            val time = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            return ActivityEntry(type, date, time, null, id)
        }

        BOTTLE_FEED_REGEX.matchEntire(content)?.let { match ->
            val type = BottleType.fromString(match.groupValues[1]) ?: return null
            val date = LocalDate.parse(match.groupValues[2], DateTimeUtil.DATE_FORMAT)
            val time = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            val amountMl = match.groupValues[4].toInt()
            return BottleFeedEntry(type, date, time, amountMl, id)
        }

        NOISE_REGEX.matchEntire(content)?.let { match ->
            val date = LocalDate.parse(match.groupValues[1], DateTimeUtil.DATE_FORMAT)
            val start = LocalTime.parse(match.groupValues[2], DateTimeUtil.TIME_FORMAT)
            val end = LocalTime.parse(match.groupValues[3], DateTimeUtil.TIME_FORMAT)
            val noiseType = NoiseType.fromString(match.groupValues[4]) ?: return null
            return WhiteNoiseEntry(noiseType, date, start, end, id)
        }

        NOISE_ONGOING_REGEX.matchEntire(content)?.let { match ->
            val date = LocalDate.parse(match.groupValues[1], DateTimeUtil.DATE_FORMAT)
            val start = LocalTime.parse(match.groupValues[2], DateTimeUtil.TIME_FORMAT)
            val noiseType = NoiseType.fromString(match.groupValues[3]) ?: return null
            return WhiteNoiseEntry(noiseType, date, start, null, id)
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
        val feedEntries = mutableListOf<FeedEntry>()
        val bottleFeedEntries = mutableListOf<BottleFeedEntry>()
        val whiteNoiseEntries = mutableListOf<WhiteNoiseEntry>()
        val deletedIds = mutableSetOf<String>()
        var babyName: String? = null
        var babyBirthDate: LocalDate? = null

        content.lines().forEach { line ->
            val trimmed = line.trim()

            // Collect DEL tombstones
            DEL_REGEX.matchEntire(trimmed)?.let { match ->
                deletedIds.add(match.groupValues[1])
                return@forEach
            }

            BABY_REGEX.matchEntire(stripId(trimmed))?.let { match ->
                babyName = match.groupValues[1]
                babyBirthDate = LocalDate.parse(match.groupValues[2], DateTimeUtil.DATE_FORMAT)
                return@forEach
            }
            when (val entry = parseLine(line)) {
                is SleepEntry -> sleepEntries.add(entry)
                is DiaperEntry -> diaperEntries.add(entry)
                is ActivityEntry -> activityEntries.add(entry)
                is FeedEntry -> feedEntries.add(entry)
                is BottleFeedEntry -> bottleFeedEntries.add(entry)
                is WhiteNoiseEntry -> whiteNoiseEntries.add(entry)
            }
        }

        // Filter out entries whose IDs appear in deletedIds
        return ParsedData(
            sleepEntries = sleepEntries.filter { it.id == null || it.id !in deletedIds },
            diaperEntries = diaperEntries.filter { it.id == null || it.id !in deletedIds },
            activityEntries = activityEntries.filter { it.id == null || it.id !in deletedIds },
            feedEntries = feedEntries.filter { it.id == null || it.id !in deletedIds },
            bottleFeedEntries = bottleFeedEntries.filter { it.id == null || it.id !in deletedIds },
            whiteNoiseEntries = whiteNoiseEntries.filter { it.id == null || it.id !in deletedIds },
            babyName = babyName,
            babyBirthDate = babyBirthDate,
            deletedIds = deletedIds
        )
    }

    fun formatSleepEntry(entry: SleepEntry): String {
        val date = entry.date.format(DateTimeUtil.DATE_FORMAT)
        val start = entry.startTime.format(DateTimeUtil.TIME_FORMAT)
        val body = if (entry.endTime != null) {
            val end = entry.endTime.format(DateTimeUtil.TIME_FORMAT)
            "SLEEP $date $start - $end"
        } else {
            "SLEEP $date $start -"
        }
        return if (entry.id != null) "#${entry.id} $body" else body
    }

    fun formatFeedEntry(entry: FeedEntry): String {
        val date = entry.date.format(DateTimeUtil.DATE_FORMAT)
        val start = entry.startTime.format(DateTimeUtil.TIME_FORMAT)
        val body = if (entry.endTime != null) {
            val end = entry.endTime.format(DateTimeUtil.TIME_FORMAT)
            "${entry.typeTag} $date $start - $end"
        } else {
            "${entry.typeTag} $date $start -"
        }
        return if (entry.id != null) "#${entry.id} $body" else body
    }

    fun formatDiaperEntry(entry: DiaperEntry): String {
        val date = entry.date.format(DateTimeUtil.DATE_FORMAT)
        val time = entry.time.format(DateTimeUtil.TIME_FORMAT)
        val body = "${entry.type.name} $date $time"
        return if (entry.id != null) "#${entry.id} $body" else body
    }

    fun formatActivityEntry(entry: ActivityEntry): String {
        val date = entry.date.format(DateTimeUtil.DATE_FORMAT)
        val time = entry.time.format(DateTimeUtil.TIME_FORMAT)
        val body = if (entry.note != null) {
            "${entry.type.name} $date $time ${entry.note}"
        } else {
            "${entry.type.name} $date $time"
        }
        return if (entry.id != null) "#${entry.id} $body" else body
    }

    fun formatBottleFeedEntry(entry: BottleFeedEntry): String {
        val date = entry.date.format(DateTimeUtil.DATE_FORMAT)
        val time = entry.time.format(DateTimeUtil.TIME_FORMAT)
        val body = "${entry.type.name} $date $time ${entry.amountMl}ml"
        return if (entry.id != null) "#${entry.id} $body" else body
    }

    fun formatWhiteNoiseEntry(entry: WhiteNoiseEntry): String {
        val date = entry.date.format(DateTimeUtil.DATE_FORMAT)
        val start = entry.startTime.format(DateTimeUtil.TIME_FORMAT)
        val typeName = entry.noiseType.name.lowercase()
        val body = if (entry.endTime != null) {
            val end = entry.endTime.format(DateTimeUtil.TIME_FORMAT)
            "NOISE $date $start - $end $typeName"
        } else {
            "NOISE $date $start - $typeName"
        }
        return if (entry.id != null) "#${entry.id} $body" else body
    }

    fun formatBabyInfo(name: String, birthDate: LocalDate): String {
        return "BABY $name ${birthDate.format(DateTimeUtil.DATE_FORMAT)}"
    }
}
