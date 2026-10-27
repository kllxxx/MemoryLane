package com.kllxx.memorylane.core.photos.data.date

import com.kllxx.memorylane.core.photos.data.repository.FakeExifDateReader
import com.kllxx.memorylane.core.photos.data.repository.FakePhotoDateCache
import com.kllxx.memorylane.core.photos.domain.model.DateSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.launch
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

    /** What a refresh does: the cache first, then the files of whatever it did not have. */
    private suspend fun resolveAll(photos: List<UndatedPhoto>): Map<Long, ResolvedDate> {
        val lookup = dates.lookup(photos)
        return lookup.dates + dates.resolveMisses(lookup.misses).dates
    }

    @Test
    fun `a lookup of an empty cache makes every photo a miss and opens no file`() = runTest {
        val lookup = dates.lookup(listOf(photo(1), photo(2)))

        assertTrue(lookup.dates.isEmpty())
        assertEquals(listOf(1L, 2L), lookup.misses.map { it.id })
        assertEquals(0, lookup.cacheHits)
        assertTrue(reader.reads.isEmpty())
    }

    @Test
    fun `a missed photo is read, resolved and cached`() = runTest {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")

        val result = dates.resolveMisses(listOf(photo(1)))

        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), result.dates.getValue(1).dateTime)
        assertEquals(DateSource.EXIF, cache.entries.getValue(1).source)
        assertEquals(1, result.read)
    }

    @Test
    fun `a cache hit gives its date at once, without opening the file`() = runTest {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")
        resolveAll(listOf(photo(1)))
        reader.reads.clear()

        val lookup = dates.lookup(listOf(photo(1)))

        assertTrue(reader.reads.isEmpty())
        assertTrue(lookup.misses.isEmpty())
        assertEquals(1, lookup.cacheHits)
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), lookup.dates.getValue(1).dateTime)
    }

    @Test
    fun `a changed DATE_MODIFIED is a miss and the file is read again`() = runTest {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")
        resolveAll(listOf(photo(1, modified = 100)))
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2017:02:03 10:15:00")
        reader.reads.clear()

        val lookup = dates.lookup(listOf(photo(1, modified = 200)))
        // The old date stays in the lookup until the file is read again, so an index built from it loses nothing
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), lookup.dates.getValue(1).dateTime)
        assertEquals(listOf(1L), lookup.misses.map { it.id })
        assertEquals(setOf(1L), lookup.stale.keys)

        val result = dates.resolveMisses(lookup.misses)

        assertEquals(listOf(1L), reader.reads)
        assertEquals(LocalDateTime.of(2017, 2, 3, 10, 15, 0), result.dates.getValue(1).dateTime)
        assertEquals(200L, cache.entries.getValue(1).dateModified)
        assertTrue(lookup.changedBy(result))
    }

    @Test
    fun `an undated photo is cached too, so it is neither a date nor a miss the next time`() = runTest {
        resolveAll(listOf(photo(1, name = "photo.jpg")))
        reader.reads.clear()

        val lookup = dates.lookup(listOf(photo(1, name = "photo.jpg")))

        assertNull(lookup.dates[1])
        assertTrue(lookup.misses.isEmpty())
        assertEquals(DateSource.UNDATED, cache.entries.getValue(1).source)
        assertTrue(reader.reads.isEmpty())
    }

    @Test
    fun `entries of photos that are no longer undated are removed by a lookup`() = runTest {
        resolveAll(listOf(photo(1), photo(2)))

        dates.lookup(listOf(photo(2)))

        assertEquals(setOf(2L), cache.entries.keys)
    }

    @Test
    fun `the counts say where the dates came from`() = runTest {
        reader.tags[1] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")

        val result = dates.resolveMisses(listOf(photo(1), photo(2, name = "IMG_20160107_101500.jpg"), photo(3, name = "x.jpg")))

        assertEquals(3, result.read)
        assertEquals(1, result.fromExif)
        assertEquals(1, result.fromFileName)
        assertEquals(1, result.stillUndated)
        assertEquals(LocalDate.of(2016, 1, 7), result.dates.getValue(2).toLocalDate(ZoneOffset.UTC))
    }

    @Test
    fun `no photos means nothing is read and the cache is emptied`() = runTest {
        resolveAll(listOf(photo(1)))

        val lookup = dates.lookup(emptyList())

        assertTrue(lookup.dates.isEmpty() && lookup.misses.isEmpty())
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `a pass that is cancelled keeps everything it read in the cache`() = runTest {
        // 120 photos: two full batches of 50 are written on the way, the 20 left are written when cancelled
        val photos = (1L..120L).map { photo(it, name = "IMG_20160107_101500.jpg") }
        val job = launch { dates.resolveMisses(photos) }
        // The reader is called on the way; cancel after the 110th
        reader.onRead = { if (reader.reads.size == 110) job.cancel() }

        val failed = runCatching { job.join() }

        assertTrue(failed.isSuccess)
        assertTrue(job.isCancelled)
        // Everything read up to the cancellation is cached, none of it is lost
        assertEquals(reader.reads.size, cache.entries.size)
        assertTrue(cache.entries.size in 100..120)
    }
}
