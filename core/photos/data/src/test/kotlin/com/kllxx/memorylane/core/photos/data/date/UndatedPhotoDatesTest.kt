package com.kllxx.memorylane.core.photos.data.date

import com.kllxx.memorylane.core.photos.data.repository.FakeExifDateReader
import com.kllxx.memorylane.core.photos.data.repository.FakePhotoDateCache
import com.kllxx.memorylane.core.photos.domain.model.DateSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndatedPhotoDatesTest {

    private val reader = FakeExifDateReader()
    private val cache = FakePhotoDateCache()
    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val dates = UndatedPhotoDates(cache, PhotoDateResolver(reader, clock))

    private fun photo(id: Long, modified: Long = 100, name: String? = null) =
        UndatedPhoto(id, modified, name, "image/jpeg")

    @Test
    fun `a photo is resolved and cached`() = runTest {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")

        val result = dates.resolve(listOf(photo(1)))

        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), result.dates.getValue(1).dateTime)
        assertEquals(DateSource.EXIF, cache.entries.getValue(1).source)
        assertEquals(1, result.cacheMisses)
        assertEquals(0, result.cacheHits)
    }

    @Test
    fun `a cache hit does not open the file`() = runTest {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")
        dates.resolve(listOf(photo(1)))
        reader.reads.clear()

        val result = dates.resolve(listOf(photo(1)))

        assertTrue(reader.reads.isEmpty())
        assertEquals(1, result.cacheHits)
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), result.dates.getValue(1).dateTime)
    }

    @Test
    fun `a changed DATE_MODIFIED reads the file again`() = runTest {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")
        dates.resolve(listOf(photo(1, modified = 100)))
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2017:02:03 10:15:00")
        reader.reads.clear()

        val result = dates.resolve(listOf(photo(1, modified = 200)))

        assertEquals(listOf(1L), reader.reads)
        assertEquals(LocalDateTime.of(2017, 2, 3, 10, 15, 0), result.dates.getValue(1).dateTime)
        assertEquals(200L, cache.entries.getValue(1).dateModified)
    }

    @Test
    fun `an undated photo is cached too, so it is not read again`() = runTest {
        val first = dates.resolve(listOf(photo(1, name = "photo.jpg")))
        reader.reads.clear()

        val second = dates.resolve(listOf(photo(1, name = "photo.jpg")))

        assertNull(first.dates[1])
        assertNull(second.dates[1])
        assertEquals(DateSource.UNDATED, cache.entries.getValue(1).source)
        assertTrue(reader.reads.isEmpty())
        assertEquals(1, second.stillUndated)
    }

    @Test
    fun `entries of photos that are no longer undated are removed`() = runTest {
        dates.resolve(listOf(photo(1), photo(2)))

        dates.resolve(listOf(photo(2)))

        assertEquals(setOf(2L), cache.entries.keys)
    }

    @Test
    fun `the counts say where the dates came from`() = runTest {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")

        val result = dates.resolve(listOf(photo(1), photo(2, name = "IMG_20160107_101500.jpg"), photo(3, name = "x.jpg")))

        assertEquals(3, result.undated)
        assertEquals(1, result.fromExif)
        assertEquals(1, result.fromFileName)
        assertEquals(1, result.stillUndated)
        assertEquals(LocalDate.of(2016, 1, 7), result.dates.getValue(2).toLocalDate(ZoneOffset.UTC))
    }

    @Test
    fun `no photos means nothing is read and the cache is emptied`() = runTest {
        dates.resolve(listOf(photo(1)))

        val result = dates.resolve(emptyList())

        assertTrue(result.dates.isEmpty())
        assertTrue(cache.entries.isEmpty())
    }
}
