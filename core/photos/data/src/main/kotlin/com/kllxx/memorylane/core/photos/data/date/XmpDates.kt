package com.kllxx.memorylane.core.photos.data.date

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** A date from an XMP field. [hasTime] is false when the field had only a day, so the time of [dateTime] is noon. */
internal class XmpDate(val field: String, val dateTime: LocalDateTime, val offset: ZoneOffset?, val hasTime: Boolean)

/** The XMP fields that hold the moment a photo was made, in the order they are trusted. */
private val xmpFields = listOf("xmp:CreateDate", "photoshop:DateCreated", "exif:DateTimeOriginal")

private val xmpDateTime = Regex(
    """^\s*(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:[.,]\d+)?)?\s*(Z|[+-]\d{2}:?\d{2})?)?\s*$""",
)

/** The usable dates among the XMP fields in [xmp], written either as attributes or as elements; empty for no packet. */
internal fun parseXmpDates(xmp: String?): List<XmpDate> {
    if (xmp.isNullOrBlank()) return emptyList()
    return xmpFields.mapNotNull { field ->
        val name = Regex.escape(field)
        val value = Regex("""$name\s*=\s*["']([^"']*)["']""").find(xmp)?.groupValues?.get(1)
            ?: Regex("""<$name[^>]*>\s*([^<]*?)\s*</$name>""").find(xmp)?.groupValues?.get(1)
        parseXmpDateTime(value)?.let { (dateTime, offset, hasTime) -> XmpDate(field, dateTime, offset, hasTime) }
    }
}

/** `2016-01-07T10:15:00+03:00`, `...Z`, `2016-01-07T10:15` or `2016-01-07`; null for anything else or an impossible date. */
internal fun parseXmpDateTime(value: String?): Triple<LocalDateTime, ZoneOffset?, Boolean>? {
    val match = xmpDateTime.find(value.orEmpty()) ?: return null
    val (year, month, day, hour, minute, second, zone) = match.destructured
    return try {
        val date = LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), 0, 0)
        if (hour.isEmpty()) {
            Triple(date.with(LocalTime.NOON), null, false)
        } else {
            val offset = when (zone) {
                "" -> null
                "Z" -> ZoneOffset.UTC
                else -> ZoneOffset.of(zone)
            }
            val dateTime = date.withHour(hour.toInt()).withMinute(minute.toInt()).withSecond(second.ifEmpty { "0" }.toInt())
            Triple(dateTime, offset, true)
        }
    } catch (e: DateTimeException) {
        null
    }
}
