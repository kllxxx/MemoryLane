package com.kllxx.memorylane.core.photos.data.date

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XmpDatesTest {

    private val jan7 = LocalDateTime.of(2016, 1, 7, 10, 15, 0)

    private fun parse(value: String?) = parseXmpDateTime(value)

    @Test
    fun `a date and time with an offset`() {
        assertEquals(Triple(jan7, ZoneOffset.ofHours(3), true), parse("2016-01-07T10:15:00+03:00"))
        assertEquals(Triple(jan7, ZoneOffset.ofHoursMinutes(-5, -30), true), parse("2016-01-07T10:15:00-05:30"))
        assertEquals(Triple(jan7, ZoneOffset.ofHours(3), true), parse("2016-01-07T10:15:00+0300"))
    }

    @Test
    fun `Z is UTC`() {
        assertEquals(Triple(jan7, ZoneOffset.UTC, true), parse("2016-01-07T10:15:00Z"))
    }

    @Test
    fun `a date and time without an offset is a wall-clock time`() {
        assertEquals(Triple(jan7, null, true), parse("2016-01-07T10:15:00"))
        assertEquals(Triple(jan7, null, true), parse("2016-01-07 10:15:00"))
    }

    @Test
    fun `seconds are optional and fractions are dropped`() {
        assertEquals(Triple(LocalDateTime.of(2016, 1, 7, 10, 15, 0), null, true), parse("2016-01-07T10:15"))
        assertEquals(Triple(LocalDateTime.of(2016, 1, 7, 10, 15, 30), null, true), parse("2016-01-07T10:15:30.987"))
    }

    @Test
    fun `a day alone has no time, and noon is a placeholder`() {
        assertEquals(Triple(LocalDateTime.of(2016, 1, 7, 12, 0, 0), null, false), parse("2016-01-07"))
    }

    @Test
    fun `a real midnight has a time`() {
        assertEquals(Triple(LocalDateTime.of(2016, 1, 7, 0, 0, 0), null, true), parse("2016-01-07T00:00:00"))
    }

    @Test
    fun `missing, partial and impossible values are null`() {
        assertNull(parse(null))
        assertNull(parse(""))
        assertNull(parse("2016"))
        assertNull(parse("2016-01"))
        assertNull(parse("not a date"))
        assertNull(parse("0000-00-00T00:00:00"))
        assertNull(parse("2016-13-07T10:15:00"))
        assertNull(parse("2016-01-07T25:15:00"))
        assertNull(parse("2016-01-07T10:15:00+25:00"))
    }

    @Test
    fun `the fields are read as attributes`() {
        val xmp = """<rdf:Description xmp:CreateDate="2016-01-07T10:15:00+03:00" photoshop:DateCreated="2017-02-02T11:00:00"
            exif:DateTimeOriginal="2018-03-03T12:00:00"/>"""

        val dates = parseXmpDates(xmp)

        assertEquals(listOf("xmp:CreateDate", "photoshop:DateCreated", "exif:DateTimeOriginal"), dates.map { it.field })
        assertEquals(ZoneOffset.ofHours(3), dates[0].offset)
        assertEquals(LocalDateTime.of(2017, 2, 2, 11, 0, 0), dates[1].dateTime)
        assertEquals(LocalDateTime.of(2018, 3, 3, 12, 0, 0), dates[2].dateTime)
    }

    @Test
    fun `the fields are read as elements`() {
        val xmp = """<xmp:CreateDate>2016-01-07T10:15:00Z</xmp:CreateDate>
            <exif:DateTimeOriginal> 2018-03-03T12:00:00 </exif:DateTimeOriginal>"""

        val dates = parseXmpDates(xmp)

        assertEquals(listOf("xmp:CreateDate", "exif:DateTimeOriginal"), dates.map { it.field })
        assertEquals(jan7 to ZoneOffset.UTC, dates[0].dateTime to dates[0].offset)
        assertEquals(LocalDateTime.of(2018, 3, 3, 12, 0, 0), dates[1].dateTime)
    }

    @Test
    fun `the order is CreateDate, DateCreated, DateTimeOriginal whatever the order in the packet`() {
        val xmp = """exif:DateTimeOriginal="2018-03-03T12:00:00" photoshop:DateCreated="2017-02-02T11:00:00" xmp:CreateDate="2016-01-07T10:15:00""""

        assertEquals(listOf("xmp:CreateDate", "photoshop:DateCreated", "exif:DateTimeOriginal"), parseXmpDates(xmp).map { it.field })
    }

    @Test
    fun `a field with a bad value is skipped and the others are kept`() {
        val xmp = """xmp:CreateDate="garbage" photoshop:DateCreated="2017-02-02T11:00:00""""

        assertEquals(listOf("photoshop:DateCreated"), parseXmpDates(xmp).map { it.field })
    }

    @Test
    fun `other date fields are not read`() {
        val xmp = """xmp:ModifyDate="2016-01-07T10:15:00" xmp:MetadataDate="2016-01-07T10:15:00" tiff:DateTime="2016-01-07T10:15:00""""

        assertTrue(parseXmpDates(xmp).isEmpty())
    }

    @Test
    fun `no packet has no dates`() {
        assertTrue(parseXmpDates(null).isEmpty())
        assertTrue(parseXmpDates("").isEmpty())
        assertTrue(parseXmpDates("<x:xmpmeta><xmp:CreatorTool>Canva</xmp:CreatorTool></x:xmpmeta>").isEmpty())
    }
}
