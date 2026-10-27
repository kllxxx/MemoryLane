package com.kllxx.memorylane.core.photos.data.date

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Finds the date that camera, messenger and screenshot apps write into file names:
 * `IMG_20160107_101500.jpg`, `IMG-20160107-WA0001.jpg`, `Screenshot_2016-01-07-10-15-00_app.png`,
 * `PXL_20160107_101500123.jpg` and the plain `20160107_101500.jpg`.
 *
 * Only the start of the name is read, so a number inside a longer name is not taken for a date. A name with a known
 * prefix needs a date; a plain name needs a date and a time, which keeps ordinary numbers from being read as dates.
 * Plausibility (not before 1990, not in the future) is the caller's decision.
 */
internal object FileNameDateParser {

    private val pattern = Regex(
        """^((?:IMG|PXL|Screenshot)[_-])?(\d{4})-?(\d{2})-?(\d{2})(?!\d)(?:[_T-]?(\d{2})[-:.]?(\d{2})[-:.]?(\d{2}))?""",
        RegexOption.IGNORE_CASE,
    )

    /** A name without a time gives noon, so that the day does not change with the time zone. */
    private val noon = LocalTime.NOON

    fun parse(name: String?): LocalDateTime? {
        val match = pattern.find(name?.trim().orEmpty()) ?: return null
        val (prefix, year, month, day, hour, minute, second) = match.destructured
        if (prefix.isEmpty() && hour.isEmpty()) return null
        return try {
            val date = LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), 0, 0)
            if (hour.isEmpty()) {
                date.with(noon)
            } else {
                date.withHour(hour.toInt()).withMinute(minute.toInt()).withSecond(second.toInt())
            }
        } catch (e: DateTimeException) {
            // Month 13, day 32 and the like: a number that only looks like a date
            null
        }
    }
}
