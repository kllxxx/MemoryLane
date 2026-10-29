package com.kllxx.memorylane.core.photos.data.date

import java.time.LocalDateTime
import java.time.ZoneOffset
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
        assertNull(parse("123456789.jpg"))
        assertNull(parse("12345678901234.jpg"))
    }

    @Test
    fun `names without a date`() {
        assertNull(parse("photo.jpg"))
        assertNull(parse("IMG_1234.jpg"))
        assertNull(parse(""))
        assertNull(parse(null))
    }

    @Test
    fun `a date without a time inside a longer name is not read`() {
        assertNull(parse("holiday_20160107.jpg"))
        assertNull(parse("holiday_2016-01-07.jpg"))
    }

    // --- the patterns that are read anywhere in the name ---

    private val jan7 = LocalDateTime.of(2016, 1, 7, 10, 15, 0)

    @Test
    fun `Samsung screenshot with a compact date and time`() {
        assertEquals(FileNameDate(jan7, hasTime = true), FileNameDateParser.parseDetailed("Screenshot_20160107-101500.jpg"))
        assertEquals(FileNameDate(jan7, hasTime = true), FileNameDateParser.parseDetailed("Screenshot_20160107-101500_Instagram.jpg"))
    }

    @Test
    fun `Telegram photo names`() {
        assertEquals(FileNameDate(jan7, hasTime = true), FileNameDateParser.parseDetailed("photo_2016-01-07_10-15-00.jpg"))
        assertEquals(FileNameDate(jan7, hasTime = true), FileNameDateParser.parseDetailed("photo_2016-01-07_10-15-00 (2).jpg"))
    }

    @Test
    fun `Signal names have the time without separators`() {
        assertEquals(FileNameDate(jan7, hasTime = true), FileNameDateParser.parseDetailed("signal-2016-01-07-101500.jpg"))
        assertEquals(FileNameDate(jan7, hasTime = true), FileNameDateParser.parseDetailed("signal-2016-01-07-101500-2.jpg"))
    }

    @Test
    fun `a dashed date and time anywhere with the separators between date and time and in the time`() {
        assertEquals(jan7, parse("holiday 2016-01-07 10.15.00.jpg"))
        assertEquals(jan7, parse("holiday_2016-01-07_10-15-00.jpg"))
        assertEquals(jan7, parse("holiday-2016-01-07T10:15:00.jpg"))
        assertEquals(jan7, parse("holiday_2016-01-07_101500.jpg"))
    }

    @Test
    fun `a compact date and time anywhere in the name`() {
        assertEquals(jan7, parse("holiday_20160107_101500.jpg"))
        assertEquals(jan7, parse("holiday-20160107-101500-edit.jpg"))
        assertEquals(jan7, parse("a 20160107_101500.jpg"))
    }

    @Test
    fun `the compact and dashed patterns do not cut a longer number`() {
        assertNull(parse("x120160107_101500.jpg"))
        assertNull(parse("x20160107_1015001.jpg"))
        assertNull(parse("x2016-01-07_1015001.jpg"))
    }

    @Test
    fun `impossible dates in the generic patterns are skipped`() {
        assertNull(parse("holiday_2016-13-07_10-15-00.jpg"))
        assertNull(parse("holiday_20160132_101500.jpg"))
        assertNull(parse("holiday_20160107_256000.jpg"))
    }

    @Test
    fun `a later valid date is found when an earlier one is impossible`() {
        assertEquals(jan7, parse("2016-13-07_10-15-00 and 2016-01-07_10-15-00.jpg"))
    }

    @Test
    fun `the start of the name comes before the generic patterns`() {
        val names = FileNameDateParser.candidates("IMG_20160107_101500 2017-02-02_11-00-00.jpg").toList()

        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), names[0].dateTime)
        assertEquals(LocalDateTime.of(2017, 2, 2, 11, 0, 0), names[1].dateTime)
    }

    @Test
    fun `a Unix timestamp in seconds is a UTC moment`() {
        val date = FileNameDateParser.parseDetailed("1452161700.jpg")!!

        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), date.dateTime)
        assertEquals(ZoneOffset.UTC, date.offset)
        assertEquals(true, date.hasTime)
    }

    @Test
    fun `a Unix timestamp in milliseconds is a UTC moment`() {
        val date = FileNameDateParser.parseDetailed("IMG_1452161700123.jpg")!!

        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), date.dateTime)
        assertEquals(ZoneOffset.UTC, date.offset)
    }

    @Test
    fun `seconds count only as the whole name, with or without an extension`() {
        val expected = LocalDateTime.of(2016, 1, 7, 18, 35, 11)
        assertEquals(expected, parse("1452191711.jpg"))
        assertEquals(expected, parse("1452191711.JPEG"))
        assertEquals(expected, parse("1452191711.webp"))
        assertEquals(expected, parse("1452191711"))
        assertEquals(expected, parse("  1452191711.jpg "))
    }

    @Test
    fun `a 10-digit number inside a longer name is an ID, not a date`() {
        assertNull(parse("vk_1452191711_photo.jpg"))
        assertNull(parse("IMG_1452191711.jpg"))
        assertNull(parse("1452191711_1.jpg"))
        assertNull(parse("1452191711 (2).jpg"))
        assertNull(parse("photo 1452191711.jpg"))
        assertNull(parse("1452191711.backup.jpg"))
        assertNull(parse("1452191711-edit.png"))
    }

    @Test
    fun `milliseconds count inside longer names, standing alone`() {
        val expected = LocalDateTime.of(2016, 1, 7, 18, 35, 11)
        assertEquals(expected, parse("vk_1452191711123_photo.jpg"))
        assertEquals(expected, parse("1452191711123.jpg"))
        assertEquals(expected, parse("photo 1452191711123 (2).jpg"))
        assertNull(parse("vk1452191711123_photo.jpg"))
        assertNull(parse("vk_14521917111234_photo.jpg"))
    }

    @Test
    fun `a timestamp inside a longer number or word is not read`() {
        assertNull(parse("x14521617001.jpg"))
        assertNull(parse("145216170.jpg"))
        assertNull(parse("a1452161700.jpg"))
        assertNull(parse("1452161700a.jpg"))
        assertNull(parse("a1452161700123.jpg"))
        assertNull(parse("1452161700123a.jpg"))
    }

    @Test
    fun `a timestamp is a candidate after the patterns with a date`() {
        val names = FileNameDateParser.candidates("1452161700000 2017-02-02_11-00-00.jpg").toList()

        assertEquals(LocalDateTime.of(2017, 2, 2, 11, 0, 0), names[0].dateTime)
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), names[1].dateTime)
    }

    @Test
    fun `only the names with a wall-clock date have no offset`() {
        assertNull(FileNameDateParser.parseDetailed("signal-2016-01-07-101500.jpg")!!.offset)
        assertNull(FileNameDateParser.parseDetailed("holiday_20160107_101500.jpg")!!.offset)
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
