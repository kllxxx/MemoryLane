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
}
