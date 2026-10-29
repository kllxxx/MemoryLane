package com.kllxx.memorylane.core.photos.data.date

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
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

    // --- GPS ---

    @Test
    fun `the GPS date and time are a UTC moment`() {
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), parseGpsDateTime("2016:01:07", "10/1,15/1,0/1"))
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 30), parseGpsDateTime("2016:01:07", "10/1,15/1,30/1"))
    }

    @Test
    fun `GPS seconds may be a fraction, and the parts may have a different denominator`() {
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 30), parseGpsDateTime("2016:01:07", "10/1,15/1,30500/1000"))
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), parseGpsDateTime("2016:01:07", "10,15,0"))
    }

    @Test
    fun `the GPS time may be written with colons`() {
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 30), parseGpsDateTime("2016:01:07", "10:15:30"))
    }

    @Test
    fun `a missing, malformed or impossible GPS moment is null`() {
        assertNull(parseGpsDateTime(null, "10/1,15/1,0/1"))
        assertNull(parseGpsDateTime("2016:01:07", null))
        assertNull(parseGpsDateTime("", ""))
        assertNull(parseGpsDateTime("2016:01:07", "10/1,15/1"))
        assertNull(parseGpsDateTime("2016:01:07", "10/1,15/1,0/1,5/1"))
        assertNull(parseGpsDateTime("2016:01:07", "10/0,15/1,0/1"))
        assertNull(parseGpsDateTime("2016:01:07", "ten,15,0"))
        assertNull(parseGpsDateTime("2016:13:07", "10/1,15/1,0/1"))
        assertNull(parseGpsDateTime("0000:00:00", "10/1,15/1,0/1"))
        assertNull(parseGpsDateTime("2016:01:07", "25/1,15/1,0/1"))
        assertNull(parseGpsDateTime("2016:01:07", "10/1,60/1,0/1"))
    }

    @Test
    fun `the tags give the GPS moment`() {
        assertEquals(
            LocalDateTime.of(2016, 1, 7, 10, 15, 0),
            ExifDateTags(gpsDate = "2016:01:07", gpsTime = "10/1,15/1,0/1").gpsDateTime(),
        )
        assertNull(ExifDateTags(gpsDate = "2016:01:07").gpsDateTime())
    }

    @Test
    fun `the GPS date and time are read from a file`() {
        val bytes = jpegWith(
            ExifInterface.TAG_GPS_DATESTAMP to "2016:01:07",
            ExifInterface.TAG_GPS_TIMESTAMP to "10:15:00",
        )

        val tags = readExifDateTags(ByteArrayInputStream(bytes))

        assertEquals("2016:01:07", tags.gpsDate)
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), tags.gpsDateTime())
    }

    // --- XMP ---

    @Test
    fun `the XMP of a JPEG is read`() {
        val tags = readExifDateTags(ByteArrayInputStream(jpegWithXmp(XMP)))

        assertEquals(listOf("xmp:CreateDate", "exif:DateTimeOriginal"), parseXmpDates(tags.xmp).map { it.field })
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), parseXmpDates(tags.xmp).first().dateTime)
    }

    // --- PNG (and, through ExifInterface's own parsers, WebP): HEIC needs API 28 and a real media framework, so it is not here ---

    @Test
    fun `the EXIF of a PNG is read`() {
        val file = File(folder.root, "test.png")
        file.writeBytes(png())
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2016:01:07 10:15:00")
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+03:00")
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, "2016:01:07")
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, "10:15:00")
        exif.saveAttributes()

        val tags = readExifDateTags(ByteArrayInputStream(file.readBytes()))

        assertEquals("2016:01:07 10:15:00", tags.dateTimeOriginal)
        assertEquals("+03:00", tags.offsetTimeOriginal)
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), tags.gpsDateTime())
    }

    @Test
    fun `the XMP of a PNG is read`() {
        val itxt = "XML:com.adobe.xmp".toByteArray() + byteArrayOf(0, 0, 0, 0, 0) + XMP.toByteArray()

        val tags = readExifDateTags(ByteArrayInputStream(png("iTXt" to itxt)))

        assertEquals(listOf("xmp:CreateDate", "exif:DateTimeOriginal"), parseXmpDates(tags.xmp).map { it.field })
    }

    @Test
    fun `a PNG with only a tool name in its XMP has no date`() {
        val xmp = "<x:xmpmeta><xmp:CreatorTool>Canva</xmp:CreatorTool></x:xmpmeta>"
        val itxt = "XML:com.adobe.xmp".toByteArray() + byteArrayOf(0, 0, 0, 0, 0) + xmp.toByteArray()

        val tags = readExifDateTags(ByteArrayInputStream(png("iTXt" to itxt)))

        assertEquals(emptyList<XmpDate>(), parseXmpDates(tags.xmp))
        assertNull(tags.dateTimeOriginal)
    }

    @Test
    fun `a file with no EXIF has no tags`() {
        val tags = readExifDateTags(ByteArrayInputStream(MINIMAL_JPEG))

        assertEquals(ExifDateTags(), tags)
    }

    private fun jpegWithXmp(xmp: String): ByteArray {
        val payload = "http://ns.adobe.com/xap/1.0/".toByteArray() + byteArrayOf(0) + xmp.toByteArray()
        val length = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        // Behind SOI and the JFIF header, before EOI
        return MINIMAL_JPEG.copyOfRange(0, 20) + app1 + MINIMAL_JPEG.copyOfRange(20, MINIMAL_JPEG.size)
    }

    /** A 1x1 PNG with the [extraChunks] (type to data) in front of the image data. */
    private fun png(vararg extraChunks: Pair<String, ByteArray>): ByteArray {
        fun chunk(type: String, data: ByteArray): ByteArray {
            val body = type.toByteArray() + data
            val crc = CRC32().apply { update(body) }.value
            return ByteBuffer.allocate(12 + data.size)
                .putInt(data.size).put(body).putInt(crc.toInt())
                .array()
        }
        val header = byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 0, 0, 0, 0)
        val pixel = byteArrayOf(0x78, 0x9C.toByte(), 0x63, 0x60, 0, 0, 0, 2, 0, 1)
        return byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            chunk("IHDR", header) +
            extraChunks.fold(ByteArray(0)) { all, (type, data) -> all + chunk(type, data) } +
            chunk("IDAT", pixel) +
            chunk("IEND", ByteArray(0))
    }

    private companion object {
        const val XMP = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF><rdf:Description xmp:CreateDate="2016-01-07T10:15:00"
            exif:DateTimeOriginal="2017-02-02T11:00:00"/></rdf:RDF></x:xmpmeta>"""

        /** SOI, a JFIF header and EOI: the smallest file ExifInterface accepts as a JPEG. */
        val MINIMAL_JPEG = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xD9.toByte(),
        )
    }
}
