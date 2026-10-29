package com.kllxx.memorylane.core.photos.data.date

import com.kllxx.memorylane.core.photos.data.repository.FakeExifDateReader
import com.kllxx.memorylane.core.photos.domain.model.DateSource
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoDateResolverTest {

    private val reader = FakeExifDateReader()
    private val now = LocalDate.of(2026, 10, 15).atTime(12, 0)
    private val clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val resolver = PhotoDateResolver(reader, clock)

    private fun photo(name: String? = null) = UndatedPhoto(id = 1, dateModified = 100, name = name, mimeType = "image/jpeg")

    private val jan7 = LocalDateTime.of(2016, 1, 7, 10, 15, 0)

    @Test
    fun `DateTimeOriginal comes first`() {
        reader.tags[1] = ExifDateTags(
            dateTimeOriginal = "2016:01:07 10:15:00",
            dateTimeDigitized = "2017:02:02 10:15:00",
            dateTime = "2018:03:03 10:15:00",
        )

        assertEquals(ResolvedDate(jan7, null, DateSource.EXIF), resolver.resolve(photo()))
    }

    @Test
    fun `DateTimeDigitized is used when there is no DateTimeOriginal`() {
        reader.tags[1] = ExifDateTags(dateTimeDigitized = "2016:01:07 10:15:00", dateTime = "2018:03:03 10:15:00")

        assertEquals(jan7, resolver.resolve(photo())!!.dateTime)
    }

    @Test
    fun `DateTime is the last EXIF tag`() {
        reader.tags[1] = ExifDateTags(dateTime = "2016:01:07 10:15:00")

        assertEquals(jan7, resolver.resolve(photo())!!.dateTime)
    }

    @Test
    fun `the offset of the tag is used when present`() {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00", offsetTimeOriginal = "+03:00")

        val resolved = resolver.resolve(photo())!!

        assertEquals(ZoneOffset.ofHours(3), resolved.offset)
        assertEquals(LocalDateTime.of(2016, 1, 7, 7, 15, 0).toInstant(ZoneOffset.UTC), resolved.toInstant(ZoneOffset.UTC))
    }

    @Test
    fun `without an offset the device zone applies`() {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")

        val resolved = resolver.resolve(photo())!!

        assertNull(resolved.offset)
        assertEquals(jan7.toInstant(ZoneOffset.ofHours(9)), resolved.toInstant(ZoneOffset.ofHours(9)))
    }

    @Test
    fun `an offset of another tag is not borrowed`() {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00", offsetTime = "+03:00")

        assertNull(resolver.resolve(photo())!!.offset)
    }

    @Test
    fun `a bad offset is ignored but the date is kept`() {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00", offsetTimeOriginal = "garbage")

        val resolved = resolver.resolve(photo())!!

        assertNull(resolved.offset)
        assertEquals(jan7, resolved.dateTime)
    }

    @Test
    fun `EXIF beats the file name`() {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")

        val resolved = resolver.resolve(photo("IMG_20200505_101500.jpg"))!!

        assertEquals(DateSource.EXIF, resolved.source)
        assertEquals(jan7, resolved.dateTime)
    }

    @Test
    fun `the file name is used when the EXIF data has no date or cannot be read`() {
        // No tags set: the fake reader fails like an unreadable file
        assertEquals(DateSource.FILENAME, resolver.resolve(photo("IMG_20160107_101500.jpg"))!!.source)

        reader.tags[1] = ExifDateTags()
        val resolved = resolver.resolve(photo("IMG_20160107_101500.jpg"))!!
        assertEquals(DateSource.FILENAME, resolved.source)
        assertEquals(jan7, resolved.dateTime)
    }

    @Test
    fun `nothing found means undated`() {
        assertNull(resolver.resolve(photo("photo.jpg")))
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "    :  :     :  :  ")
        assertNull(resolver.resolve(photo(null)))
    }

    @Test
    fun `an implausible EXIF date falls through to the next tag`() {
        reader.tags[1] = ExifDateTags(
            dateTimeOriginal = "1970:01:01 00:00:00",
            dateTimeDigitized = "2030:01:01 00:00:00",
            dateTime = "2016:01:07 10:15:00",
        )

        assertEquals(jan7, resolver.resolve(photo())!!.dateTime)
    }

    @Test
    fun `an implausible EXIF date falls through to the file name`() {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "1980:01:01 00:00:00")

        assertEquals(DateSource.FILENAME, resolver.resolve(photo("IMG_20160107_101500.jpg"))!!.source)
    }

    @Test
    fun `dates before 1990 and in the future are rejected, also from the file name`() {
        assertNull(resolver.resolve(photo("IMG_19891231_235959.jpg")))
        assertNull(resolver.resolve(photo("IMG_20261016_000000.jpg")))
        assertNull(resolver.resolve(photo("IMG_20261015_130000.jpg")))
    }

    @Test
    fun `the first day of 1990 and the present moment are plausible`() {
        assertNotNull(resolver.resolve(photo("IMG_19900101_000000.jpg")))
        assertNotNull(resolver.resolve(photo("IMG_20261015_120000.jpg")))
    }

    // --- GPS ---

    @Test
    fun `the GPS moment is UTC and the day is the one of the device zone`() {
        reader.tags[1] = ExifDateTags(gpsDate = "2016:01:07", gpsTime = "22/1,30/1,0/1")

        val resolved = resolver.resolve(photo())!!

        assertEquals(DateSource.GPS, resolved.source)
        assertEquals(ZoneOffset.UTC, resolved.offset)
        assertEquals(true, resolved.hasTime)
        assertEquals(LocalDate.of(2016, 1, 7), resolved.toLocalDate(ZoneOffset.UTC))
        assertEquals(LocalDate.of(2016, 1, 8), resolved.toLocalDate(ZoneOffset.ofHours(3)))
        assertEquals(LocalDate.of(2016, 1, 7), resolved.toLocalDate(ZoneOffset.ofHours(-5)))
    }

    @Test
    fun `EXIF date tags beat GPS`() {
        reader.tags[1] = ExifDateTags(dateTime = "2016:01:07 10:15:00", gpsDate = "2020:05:05", gpsTime = "1/1,2/1,3/1")

        assertEquals(DateSource.EXIF, resolver.resolve(photo())!!.source)
    }

    @Test
    fun `GPS beats XMP and the file name`() {
        reader.tags[1] = ExifDateTags(
            gpsDate = "2016:01:07",
            gpsTime = "10/1,15/1,0/1",
            xmp = """xmp:CreateDate="2017-02-02T11:00:00"""",
        )

        val resolved = resolver.resolve(photo("IMG_20200505_101500.jpg"))!!

        assertEquals(DateSource.GPS, resolved.source)
        assertEquals(jan7, resolved.dateTime)
    }

    @Test
    fun `an implausible or incomplete GPS moment falls through`() {
        reader.tags[1] = ExifDateTags(gpsDate = "1970:01:01", gpsTime = "0/1,0/1,0/1")
        assertNull(resolver.resolve(photo()))

        reader.tags[1] = ExifDateTags(gpsDate = "2016:01:07")
        assertNull(resolver.resolve(photo()))

        reader.tags[1] = ExifDateTags(gpsDate = "2030:01:01", gpsTime = "0/1,0/1,0/1", xmp = """xmp:CreateDate="2016-01-07T10:15:00"""")
        assertEquals(DateSource.XMP, resolver.resolve(photo())!!.source)
    }

    // --- XMP ---

    @Test
    fun `an XMP date is used with its offset`() {
        reader.tags[1] = ExifDateTags(xmp = """xmp:CreateDate="2016-01-07T10:15:00+03:00"""")

        val resolved = resolver.resolve(photo())!!

        assertEquals(DateSource.XMP, resolved.source)
        assertEquals(jan7, resolved.dateTime)
        assertEquals(ZoneOffset.ofHours(3), resolved.offset)
        assertEquals(true, resolved.hasTime)
    }

    @Test
    fun `the XMP fields are tried in the order CreateDate, DateCreated, DateTimeOriginal`() {
        val xmp = """photoshop:DateCreated="2017-02-02T11:00:00" exif:DateTimeOriginal="2018-03-03T12:00:00""""
        reader.tags[1] = ExifDateTags(xmp = xmp)
        assertEquals(LocalDateTime.of(2017, 2, 2, 11, 0, 0), resolver.resolve(photo())!!.dateTime)

        reader.tags[1] = ExifDateTags(xmp = xmp + """ xmp:CreateDate="2016-01-07T10:15:00"""")
        assertEquals(jan7, resolver.resolve(photo())!!.dateTime)

        reader.tags[1] = ExifDateTags(xmp = """exif:DateTimeOriginal="2018-03-03T12:00:00"""")
        assertEquals(LocalDateTime.of(2018, 3, 3, 12, 0, 0), resolver.resolve(photo())!!.dateTime)
    }

    @Test
    fun `an implausible XMP field falls through to the next one`() {
        reader.tags[1] = ExifDateTags(
            xmp = """xmp:CreateDate="1970-01-01T00:00:00" photoshop:DateCreated="2030-01-01T00:00:00" exif:DateTimeOriginal="2016-01-07T10:15:00"""",
        )

        assertEquals(jan7, resolver.resolve(photo())!!.dateTime)
    }

    @Test
    fun `an XMP day without a time has no time`() {
        reader.tags[1] = ExifDateTags(xmp = """photoshop:DateCreated="2016-01-07"""")

        val resolved = resolver.resolve(photo())!!

        assertEquals(LocalDate.of(2016, 1, 7), resolved.dateTime.toLocalDate())
        assertEquals(false, resolved.hasTime)
    }

    @Test
    fun `XMP beats the file name, and the file name is used when XMP has no date`() {
        reader.tags[1] = ExifDateTags(xmp = """xmp:CreateDate="2016-01-07T10:15:00"""")
        assertEquals(DateSource.XMP, resolver.resolve(photo("IMG_20200505_101500.jpg"))!!.source)

        reader.tags[1] = ExifDateTags(xmp = "<xmp:CreatorTool>Canva</xmp:CreatorTool>")
        assertEquals(DateSource.FILENAME, resolver.resolve(photo("IMG_20200505_101500.jpg"))!!.source)
        assertNull(resolver.resolve(photo("Canva.png")))
    }

    // --- the file name patterns, through the chain ---

    @Test
    fun `the generic file name patterns are plausibility checked one by one`() {
        // The first date in the name is in the future, the second is real
        val resolved = resolver.resolve(photo("2030-01-01_10-00-00 and 2016-01-07_10-15-00.jpg"))!!

        assertEquals(jan7, resolved.dateTime)
        assertEquals(DateSource.FILENAME, resolved.source)
    }

    @Test
    fun `Telegram, Signal and Samsung names are dated`() {
        assertEquals(jan7, resolver.resolve(photo("photo_2016-01-07_10-15-00.jpg"))!!.dateTime)
        assertEquals(jan7, resolver.resolve(photo("signal-2016-01-07-101500.jpg"))!!.dateTime)
        assertEquals(jan7, resolver.resolve(photo("Screenshot_20160107-101500.jpg"))!!.dateTime)
        assertEquals(jan7, resolver.resolve(photo("holiday_20160107_101500.jpg"))!!.dateTime)
    }

    @Test
    fun `a Unix timestamp in a file name is a UTC moment`() {
        val seconds = resolver.resolve(photo("1452161700.jpg"))!!
        val millis = resolver.resolve(photo("IMG_1452161700123.jpg"))!!

        assertEquals(DateSource.FILENAME, seconds.source)
        assertEquals(ZoneOffset.UTC, seconds.offset)
        assertEquals(jan7, seconds.dateTime)
        assertEquals(jan7, millis.dateTime)
        assertEquals(LocalDate.of(2016, 1, 8), seconds.toLocalDate(ZoneOffset.ofHours(14)))
    }

    @Test
    fun `an ID in a longer name stays undated, the same number as the whole name is a date`() {
        assertNull(resolver.resolve(photo("vk_1452191711_photo.jpg")))
        assertNull(resolver.resolve(photo("IMG_1452191711.jpg")))
        assertEquals(LocalDateTime.of(2016, 1, 7, 18, 35, 11), resolver.resolve(photo("1452191711.jpg"))!!.dateTime)
        assertEquals(LocalDateTime.of(2016, 1, 7, 18, 35, 11), resolver.resolve(photo("vk_1452191711123_photo.jpg"))!!.dateTime)
    }

    @Test
    fun `an implausible Unix timestamp is not a date`() {
        assertNull(resolver.resolve(photo("8005551234.jpg")))        // 2223
        assertNull(resolver.resolve(photo("0631151999.jpg")))        // 31 Dec 1989 23:59:59 UTC
        assertNull(resolver.resolve(photo("1792065601.jpg")))        // one second after the clock of the test
    }

    @Test
    fun `the timestamp bounds are 1990 and now`() {
        assertNotNull(resolver.resolve(photo("0631152000.jpg")))
        assertNotNull(resolver.resolve(photo("1792065600.jpg")))
    }

    @Test
    fun `DATE_MODIFIED is never a source`() {
        assertNull(resolver.resolve(photo("photo.jpg").copy(dateModified = 1_452_161_700)))
    }

    @Test
    fun `a cached entry round-trips`() {
        val resolved = ResolvedDate(jan7, ZoneOffset.ofHours(3), DateSource.EXIF)

        val cached = resolved.toCached(photo())

        assertEquals(resolved, ResolvedDate.fromCached(cached))
        assertEquals(1L, cached.id)
        assertEquals(100L, cached.dateModified)
    }

    @Test
    fun `an undated cache entry has no date`() {
        assertNull(ResolvedDate.fromCached(ResolvedDate.undatedToCached(photo())))
    }

    // --- whether the date has a time ---

    @Test
    fun `an EXIF date has a time`() {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")

        assertEquals(true, resolver.resolve(photo())!!.hasTime)
    }

    @Test
    fun `a real midnight in EXIF has a time`() {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:08 00:00:00")

        val resolved = resolver.resolve(photo())!!

        assertEquals(LocalDateTime.of(2016, 1, 8, 0, 0, 0), resolved.dateTime)
        assertEquals(true, resolved.hasTime)
    }

    @Test
    fun `a file name with a time has a time`() {
        val resolved = resolver.resolve(photo("IMG_20160107_101500.jpg"))!!

        assertEquals(DateSource.FILENAME, resolved.source)
        assertEquals(true, resolved.hasTime)
    }

    @Test
    fun `a file name with a date only has no time`() {
        val resolved = resolver.resolve(photo("IMG-20160107-WA0001.jpg"))!!

        assertEquals(DateSource.FILENAME, resolved.source)
        assertEquals(LocalDate.of(2016, 1, 7), resolved.dateTime.toLocalDate())
        assertEquals(false, resolved.hasTime)
    }

    @Test
    fun `EXIF wins over a file name without a time and the date has its time`() {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")

        val resolved = resolver.resolve(photo("IMG-20160107-WA0001.jpg"))!!

        assertEquals(DateSource.EXIF, resolved.source)
        assertEquals(true, resolved.hasTime)
    }

    @Test
    fun `the flag survives the cache round trip`() {
        val noTime = resolver.resolve(photo("IMG-20160107-WA0001.jpg"))!!
        val withTime = resolver.resolve(photo("IMG_20160107_101500.jpg"))!!

        assertEquals(false, ResolvedDate.fromCached(noTime.toCached(photo()))!!.hasTime)
        assertEquals(true, ResolvedDate.fromCached(withTime.toCached(photo()))!!.hasTime)
        assertEquals(false, noTime.toCached(photo()).hasTime)
    }
}
