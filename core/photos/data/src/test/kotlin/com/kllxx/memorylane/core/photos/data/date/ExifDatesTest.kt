package com.kllxx.memorylane.core.photos.data.date

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// ExifInterface needs the Android runtime
@RunWith(RobolectricTestRunner::class)
class ExifDatesTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a standard EXIF date is parsed`() {
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), parseExifDateTime("2016:01:07 10:15:00"))
    }

    @Test
    fun `missing blank and impossible dates are null`() {
        assertNull(parseExifDateTime(null))
        assertNull(parseExifDateTime(""))
        assertNull(parseExifDateTime("    :  :     :  :  "))
        assertNull(parseExifDateTime("0000:00:00 00:00:00"))
        assertNull(parseExifDateTime("2016:13:07 10:15:00"))
        assertNull(parseExifDateTime("not a date"))
    }

    @Test
    fun `offsets are parsed and bad ones are null`() {
        assertEquals(ZoneOffset.ofHours(3), parseExifOffset("+03:00"))
        assertEquals(ZoneOffset.ofHoursMinutes(-5, -30), parseExifOffset("-05:30"))
        assertNull(parseExifOffset(null))
        assertNull(parseExifOffset(""))
        assertNull(parseExifOffset("   "))
        assertNull(parseExifOffset("+0300 maybe"))
    }

    @Test
    fun `the candidates are in the order Original, Digitized, DateTime, each with its own offset`() {
        val tags = ExifDateTags(
            dateTimeOriginal = "a",
            dateTimeDigitized = "b",
            dateTime = "c",
            offsetTimeOriginal = "1",
            offsetTimeDigitized = "2",
            offsetTime = "3",
        )

        assertEquals(
            listOf(Triple("DateTimeOriginal", "a", "1"), Triple("DateTimeDigitized", "b", "2"), Triple("DateTime", "c", "3")),
            tags.candidates().map { Triple(it.tag, it.dateTime, it.offset) },
        )
    }

    @Test
    fun `a candidate with a date but no offset has no offset`() {
        val (dateTime, offset) = ExifCandidate("DateTime", "2016:01:07 10:15:00", null).parse()!!

        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), dateTime)
        assertNull(offset)
    }

    @Test
    fun `a candidate without a date has nothing even with an offset`() {
        assertNull(ExifCandidate("DateTime", null, "+03:00").parse())
    }

    // The tags below are written into a real JPEG with ExifInterface and read back through the code that reads a photo's file

    private fun jpegWith(vararg tags: Pair<String, String>): ByteArray {
        val file = File(folder.root, "test.jpg")
        file.writeBytes(MINIMAL_JPEG)
        val exif = ExifInterface(file.absolutePath)
        tags.forEach { (tag, value) -> exif.setAttribute(tag, value) }
        exif.saveAttributes()
        return file.readBytes()
    }

    @Test
    fun `every date tag and offset tag is read from a file`() {
        val bytes = jpegWith(
            ExifInterface.TAG_DATETIME_ORIGINAL to "2016:01:07 10:15:00",
            ExifInterface.TAG_DATETIME_DIGITIZED to "2016:01:07 10:15:01",
            ExifInterface.TAG_DATETIME to "2016:01:08 11:00:00",
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL to "+03:00",
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED to "+03:00",
            ExifInterface.TAG_OFFSET_TIME to "+02:00",
            ExifInterface.TAG_GPS_DATESTAMP to "2016:01:07",
        )

        val tags = readExifDateTags(ByteArrayInputStream(bytes))

        assertEquals("2016:01:07 10:15:00", tags.dateTimeOriginal)
        assertEquals("2016:01:07 10:15:01", tags.dateTimeDigitized)
        assertEquals("2016:01:08 11:00:00", tags.dateTime)
        assertEquals("+03:00", tags.offsetTimeOriginal)
        assertEquals("+03:00", tags.offsetTimeDigitized)
        assertEquals("+02:00", tags.offsetTime)
        assertEquals("2016:01:07", tags.gpsDate)
    }

    @Test
    fun `a file with no EXIF has no tags`() {
        val tags = readExifDateTags(ByteArrayInputStream(MINIMAL_JPEG))

        assertEquals(ExifDateTags(), tags)
    }

    private companion object {
        /** SOI, a JFIF header and EOI: the smallest file ExifInterface accepts as a JPEG. */
        val MINIMAL_JPEG = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xD9.toByte(),
        )
    }
}
