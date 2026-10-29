package com.kllxx.memorylane.core.photos.data.date

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Finds the date that camera, messenger and screenshot apps write into file names:
 * `IMG_20160107_101500.jpg`, `IMG-20160107-WA0001.jpg`, `Screenshot_2016-01-07-10-15-00_app.png`,
 * `PXL_20160107_101500123.jpg`, `photo_2016-01-07_10-15-00.jpg`, `signal-2016-01-07-101500.jpg` and the plain
 * `20160107_101500.jpg`.
 *
 * The candidates, in the order they are trusted:
 * 1. the start of the name: a known prefix needs a date, a plain name needs a date and a time;
 * 2. a date and a time anywhere in the name, `2016-01-07_10-15-00` (the separators of the time are optional);
 * 3. a compact `20160107_101500` anywhere in the name;
 * 4. a Unix timestamp, which is a moment in UTC: 10 digits (seconds) only when they are the whole name without the
 *    extension (`1452191711.jpg`), since a 10-digit number inside a longer name is usually an ID (`vk_1452191711_photo.jpg`);
 *    13 digits (milliseconds) anywhere, standing alone between non-alphanumerics.
 * A date without a time counts only at the start of the name behind a known prefix, which keeps ordinary numbers from
 * being read as dates. Plausibility (not before 1990, not in the future) is the caller's decision, so it can go on to
 * the next candidate.
 */
internal object FileNameDateParser {

    private val pattern = Regex(
        """^((?:IMG|PXL|Screenshot)[_-])?(\d{4})-?(\d{2})-?(\d{2})(?!\d)(?:[_T-]?(\d{2})[-:.]?(\d{2})[-:.]?(\d{2}))?""",
        RegexOption.IGNORE_CASE,
    )
    private val dashed = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})[_ T-](\d{2})[-.:]?(\d{2})[-.:]?(\d{2})(?!\d)""")
    private val compact = Regex("""(?<!\d)(\d{4})(\d{2})(\d{2})[_-](\d{2})(\d{2})(\d{2})(?!\d)""")
    private val secondsName = Regex("""^(\d{10})(?:\.[A-Za-z0-9]{1,5})?$""")
    private val millisTimestamp = Regex("""(?<![\p{L}\p{N}])(\d{13})(?![\p{L}\p{N}])""")

    /** A name without a time gives noon, so that the day does not change with the time zone. */
    private val noon = LocalTime.NOON

    fun parse(name: String?): LocalDateTime? = parseDetailed(name)?.dateTime

    /** Like [parse], and says whether the name had a time; without one the time of [FileNameDate.dateTime] is noon. */
    fun parseDetailed(name: String?): FileNameDate? = candidates(name).firstOrNull()

    /** Every date the name holds, most trusted first, so that a caller can skip one that is not plausible. */
    fun candidates(name: String?): Sequence<FileNameDate> {
        val text = name?.trim().orEmpty()
        return sequence {
            startOfName(text)?.let { yield(it) }
            yieldAll(dateTimes(dashed, text))
            yieldAll(dateTimes(compact, text))
            yieldAll(timestamps(text))
        }
    }

    private fun startOfName(text: String): FileNameDate? {
        val match = pattern.find(text) ?: return null
        val (prefix, year, month, day, hour, minute, second) = match.destructured
        if (prefix.isEmpty() && hour.isEmpty()) return null
        return try {
            val date = LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), 0, 0)
            if (hour.isEmpty()) {
                FileNameDate(date.with(noon), hasTime = false)
            } else {
                FileNameDate(date.withHour(hour.toInt()).withMinute(minute.toInt()).withSecond(second.toInt()), hasTime = true)
            }
        } catch (e: DateTimeException) {
            // Month 13, day 32 and the like: a number that only looks like a date
            null
        }
    }

    private fun dateTimes(regex: Regex, text: String): Sequence<FileNameDate> = regex.findAll(text).mapNotNull { match ->
        val (year, month, day, hour, minute, second) = match.destructured
        try {
            FileNameDate(
                LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt()),
                hasTime = true,
            )
        } catch (e: DateTimeException) {
            null
        }
    }

    private fun timestamps(text: String): Sequence<FileNameDate> {
        val seconds = secondsName.find(text)?.let { sequenceOf(it.groupValues[1].toLong()) } ?: emptySequence()
        val millis = millisTimestamp.findAll(text).map { it.value.toLong() / 1000 }
        return (seconds + millis).map {
            FileNameDate(LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC), hasTime = true, offset = ZoneOffset.UTC)
        }
    }
}

/**
 * The date in a file name. Without a time in the name, [hasTime] is false and the time of [dateTime] is noon.
 * A Unix timestamp is a moment in UTC, so it has an [offset]; the other names are wall-clock times with none.
 */
internal data class FileNameDate(val dateTime: LocalDateTime, val hasTime: Boolean, val offset: ZoneOffset? = null)
