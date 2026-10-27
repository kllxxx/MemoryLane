package com.kllxx.memorylane.core.photos.data.date

import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The raw EXIF strings that can hold the date of a photo, as stored in the file. */
internal data class ExifDateTags(
    val dateTimeOriginal: String? = null,
    val dateTimeDigitized: String? = null,
    val dateTime: String? = null,
    val offsetTimeOriginal: String? = null,
    val offsetTimeDigitized: String? = null,
    val offsetTime: String? = null,
    val gpsDate: String? = null,
    val gpsTime: String? = null,
) {
    /** The date tags in the order they are trusted, each with the offset tag that belongs to it. */
    fun candidates(): List<ExifCandidate> = listOf(
        ExifCandidate("DateTimeOriginal", dateTimeOriginal, offsetTimeOriginal),
        ExifCandidate("DateTimeDigitized", dateTimeDigitized, offsetTimeDigitized),
        ExifCandidate("DateTime", dateTime, offsetTime),
    )

    /** Every tag as stored, for the diagnostics log. */
    fun describe(): String =
        "DateTimeOriginal=$dateTimeOriginal DateTimeDigitized=$dateTimeDigitized DateTime=$dateTime " +
            "OffsetTimeOriginal=$offsetTimeOriginal OffsetTimeDigitized=$offsetTimeDigitized OffsetTime=$offsetTime " +
            "GPSDateStamp=$gpsDate GPSTimeStamp=$gpsTime"
}

internal class ExifCandidate(val tag: String, val dateTime: String?, val offset: String?) {
    /** The tag's date, with its offset when that is present and valid; null when the date is missing or not a real date. */
    fun parse(): Pair<LocalDateTime, ZoneOffset?>? {
        val parsed = parseExifDateTime(dateTime) ?: return null
        return parsed to parseExifOffset(offset)
    }
}

/** Opens a photo's file and reads its EXIF date tags. Throws when the file cannot be opened or read. */
internal fun interface ExifDateReader {
    fun read(photoId: Long): ExifDateTags
}

private val exifDateTime = Regex("""^\s*(\d{4})[:-](\d{2})[:-](\d{2})[ T](\d{2}):(\d{2}):(\d{2})""")

/** `2016:01:07 10:15:00`; null for a missing value, the blank `    :  :     ` and impossible dates like `0000:00:00`. */
internal fun parseExifDateTime(value: String?): LocalDateTime? {
    val match = exifDateTime.find(value.orEmpty()) ?: return null
    val (year, month, day, hour, minute, second) = match.destructured
    return try {
        LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
    } catch (e: DateTimeException) {
        null
    }
}

/** `+03:00`; null when absent or not an offset. */
internal fun parseExifOffset(value: String?): ZoneOffset? {
    val text = value?.trim()?.trim('\u0000').orEmpty()
    if (text.isEmpty()) return null
    return try {
        ZoneOffset.of(text)
    } catch (e: DateTimeException) {
        null
    }
}

/** Reads the date tags from the bytes of an image. */
internal fun readExifDateTags(stream: InputStream): ExifDateTags {
    val exif = ExifInterface(stream)
    return ExifDateTags(
        dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
        dateTimeDigitized = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED),
        dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME),
        offsetTimeOriginal = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
        offsetTimeDigitized = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED),
        offsetTime = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME),
        gpsDate = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP),
        gpsTime = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP),
    )
}
