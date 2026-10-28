package com.kllxx.memorylane.core.photos.data.date

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNameDateParserTest {

    private fun parse(name: String?) = FileNameDateParser.parse(name)

    @Test
    fun `camera names with date and time`() {
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), parse("IMG_20160107_101500.jpg"))
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), parse("IMG_20160107_101500_HDR.jpg"))
    }

    @Test
    fun `Pixel names ignore the milliseconds`() {
        assertEquals(LocalDateTime.of(2022, 12, 31, 23, 59, 58), parse("PXL_20221231_235958123.jpg"))
    }

    @Test
    fun `WhatsApp names have a date only and get noon`() {
        assertEquals(LocalDateTime.of(2016, 1, 7, 12, 0, 0), parse("IMG-20160107-WA0001.jpg"))
    }

    @Test
    fun `screenshot names with dashes or without`() {
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), parse("Screenshot_2016-01-07-10-15-00_app.png"))
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), parse("Screenshot_20160107-101500.png"))
        assertEquals(LocalDateTime.of(2016, 1, 7, 12, 0, 0), parse("Screenshot_2016-01-07.png"))
    }

    @Test
    fun `a plain date and time name`() {
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), parse("20160107_101500.jpg"))
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), parse("20160107-101500.jpg"))
    }

    @Test
    fun `the prefix is not case sensitive`() {
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), parse("img_20160107_101500.JPG"))
    }

    @Test
    fun `a plain name needs a time, so a bare number is not a date`() {
        assertNull(parse("20160107.jpg"))
        assertNull(parse("1234567890123.jpg"))
    }

    @Test
    fun `names without a date`() {
        assertNull(parse("photo.jpg"))
        assertNull(parse("IMG_1234.jpg"))
        assertNull(parse(""))
        assertNull(parse(null))
    }

    @Test
    fun `a date inside a longer name is not read`() {
        assertNull(parse("holiday_20160107_101500.jpg"))
    }

    @Test
    fun `numbers that are not a real date are rejected`() {
        assertNull(parse("IMG_20161301_101500.jpg"))
        assertNull(parse("IMG_20160132_101500.jpg"))
        assertNull(parse("IMG_20160230_101500.jpg"))
        assertNull(parse("IMG_20160107_256000.jpg"))
    }

    @Test
    fun `Feb 29 exists only in a leap year`() {
        assertEquals(LocalDateTime.of(2016, 2, 29, 10, 0, 0), parse("IMG_20160229_100000.jpg"))
        assertNull(parse("IMG_20150229_100000.jpg"))
    }

    // --- whether the name has a time ---

    @Test
    fun `a name with a time has a time`() {
        assertEquals(FileNameDate(LocalDateTime.of(2016, 1, 7, 10, 15, 0), hasTime = true), FileNameDateParser.parseDetailed("IMG_20160107_101500.jpg"))
        assertEquals(FileNameDate(LocalDateTime.of(2016, 1, 7, 10, 15, 0), hasTime = true), FileNameDateParser.parseDetailed("Screenshot_2016-01-07-10-15-00_app.png"))
    }

    @Test
    fun `a name with a date only has no time, and its noon is a placeholder`() {
        assertEquals(FileNameDate(LocalDateTime.of(2016, 1, 7, 12, 0, 0), hasTime = false), FileNameDateParser.parseDetailed("IMG-20160107-WA0001.jpg"))
        assertEquals(FileNameDate(LocalDateTime.of(2016, 1, 7, 12, 0, 0), hasTime = false), FileNameDateParser.parseDetailed("IMG_20160107.jpg"))
        assertEquals(FileNameDate(LocalDateTime.of(2016, 1, 7, 12, 0, 0), hasTime = false), FileNameDateParser.parseDetailed("Screenshot_2016-01-07.png"))
    }

    @Test
    fun `a name with a real noon has a time`() {
        assertEquals(FileNameDate(LocalDateTime.of(2016, 1, 7, 12, 0, 0), hasTime = true), FileNameDateParser.parseDetailed("IMG_20160107_120000.jpg"))
    }

    @Test
    fun `a name with a real midnight has a time`() {
        assertEquals(FileNameDate(LocalDateTime.of(2016, 1, 7, 0, 0, 0), hasTime = true), FileNameDateParser.parseDetailed("IMG_20160107_000000.jpg"))
    }

    @Test
    fun `a name without a date has neither`() {
        assertNull(FileNameDateParser.parseDetailed("holiday.jpg"))
        assertNull(FileNameDateParser.parseDetailed(null))
    }
}
