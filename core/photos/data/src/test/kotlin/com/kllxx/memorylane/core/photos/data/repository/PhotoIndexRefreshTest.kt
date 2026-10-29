package com.kllxx.memorylane.core.photos.data.repository

import android.Manifest
import android.app.Application
import android.provider.MediaStore
import app.cash.turbine.test
import com.kllxx.memorylane.core.common.PerfLog
import com.kllxx.memorylane.core.photos.data.date.ExifDateTags
import com.kllxx.memorylane.core.photos.data.geocoder.LocationGeocoder
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PhotoIndexRefreshTest {

    private val application: Application = RuntimeEnvironment.getApplication()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(today.atTime(12, 0).atZone(zone).toInstant(), zone)
    private val geocoder = mockk<LocationGeocoder>()
    private val reader = FakeExifDateReader()
    private val cache = FakePhotoDateCache()
    private val lines = mutableListOf<String>()

    @Before
    fun setUp() {
        FakeMediaProvider.reset()
        Robolectric.buildContentProvider(FakeMediaProvider::class.java).create(MediaStore.AUTHORITY)
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        PerfLog.sink = { lines += it }
    }

    @After
    fun tearDown() {
        PerfLog.setEnabled(false)
    }

    private fun repository(dispatcher: CoroutineDispatcher) =
        PhotoRepositoryImpl(application, geocoder, dispatcher, clock, reader, cache, FakePhotoLocationCache())

    private fun row(
        id: Long,
        taken: Long?,
        modified: Long = 100,
        name: String? = null,
        extra: Map<String, Any?> = emptyMap(),
    ) = mapOf<String, Any?>(
        MediaStore.Images.Media._ID to id,
        MediaStore.Images.Media.DATE_TAKEN to taken,
        MediaStore.Images.Media.DATE_MODIFIED to modified,
        MediaStore.Images.Media.DISPLAY_NAME to name,
        // Present on the rows so that a DATE_ADDED fallback, if it came back, would be noticed
        MediaStore.Images.Media.DATE_ADDED to millis(LocalDate.of(2024, 6, 1)) / 1_000,
    ) + extra

    private fun millis(date: LocalDate) = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun exifOriginal(value: String, offset: String? = null) =
        ExifDateTags(dateTimeOriginal = value, offsetTimeOriginal = offset)

    @Test
    fun `one refresh is exactly one query over every image, without any filter`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, millis(LocalDate.of(2024, 3, 9))))

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertEquals(1, FakeMediaProvider.queries)
        assertNull(FakeMediaProvider.lastSelection)
        assertNull(FakeMediaProvider.lastSelectionArgs)
        assertEquals(
            listOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DISPLAY_NAME,
            ),
            FakeMediaProvider.lastProjection,
        )
    }

    @Test
    fun `the index is null until the first refresh and holds the last result after it`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        assertNull(repository.onThisDayIndex.value)
        FakeMediaProvider.rows = listOf(
            row(1, millis(LocalDate.of(2024, 3, 9))),
            row(2, millis(LocalDate.of(2022, 3, 9))),
            row(3, millis(LocalDate.of(2025, 12, 1))),
        )

        repository.refreshOnThisDayIndex()

        val index = repository.onThisDayIndex.value
        assertNotNull(index)
        assertEquals(2026, index!!.currentYear)
        assertEquals(3, index.totalPhotos)
        val march = index.entries.getValue(MonthDay.of(3, 9))
        assertEquals(2, march.count)
        assertEquals(2L, march.cover.id)
        assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath("2").build(), march.cover.uri)
        assertEquals(LocalDate.of(2022, 3, 9), march.cover.takenOn)
    }

    @Test
    fun `an empty gallery gives an empty index, not null`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))

        repository.refreshOnThisDayIndex()

        assertTrue(repository.onThisDayIndex.value!!.isEmpty)
    }

    @Test
    fun `refreshes that overlap share one query`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, millis(LocalDate.of(2024, 3, 9))))

        val first = launch { repository.refreshOnThisDayIndex() }
        val second = launch { repository.refreshOnThisDayIndex() }
        advanceUntilIdle()

        assertTrue(first.isCompleted && second.isCompleted)
        assertEquals(1, FakeMediaProvider.queries)
    }

    @Test
    fun `a later refresh queries again and replaces the cached index`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, millis(LocalDate.of(2024, 3, 9))))
        repository.refreshOnThisDayIndex()
        FakeMediaProvider.rows = emptyList()

        repository.refreshOnThisDayIndex()

        assertEquals(2, FakeMediaProvider.queries)
        assertTrue(repository.onThisDayIndex.value!!.isEmpty)
    }

    @Test
    fun `without permission nothing is queried and the cache is untouched`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        shadowOf(application).denyPermissions(Manifest.permission.READ_MEDIA_IMAGES)

        try {
            repository.refreshOnThisDayIndex()
            fail("expected PermissionDeniedException")
        } catch (e: PermissionDeniedException) {
            assertEquals(0, FakeMediaProvider.queries)
            assertNull(repository.onThisDayIndex.value)
        }
    }

    // --- photos without a MediaStore date ---

    @Test
    fun `undated photos are not in the first index and no file is opened before it is shown`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(
            row(1, millis(LocalDate.of(2024, 3, 9))),
            row(2, null, name = "IMG_20160309_101500.jpg"),
        )

        repository.refreshOnThisDayIndex()

        assertEquals(1, repository.onThisDayIndex.value!!.totalPhotos)
        assertTrue(reader.reads.isEmpty())
    }

    @Test
    fun `the resolved photos arrive in one more emission of the index`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(
            row(1, millis(LocalDate.of(2024, 3, 9))),
            row(2, null, name = "IMG_20160309_101500.jpg"),
            row(3, 0L, name = "photo.jpg"),
        )
        reader.tags[3] = exifOriginal("2016:03:09 08:00:00")

        repository.onThisDayIndex.test {
            assertNull(awaitItem())
            repository.refreshOnThisDayIndex()
            assertEquals(1, awaitItem()!!.totalPhotos)

            advanceUntilIdle()

            val resolved = awaitItem()!!
            assertEquals(3, resolved.totalPhotos)
            assertEquals(3, resolved.entries.getValue(MonthDay.of(3, 9)).count)
            expectNoEvents()
        }
    }

    @Test
    fun `EXIF beats the file name`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "IMG_20200101_101500.jpg"))
        reader.tags[2] = exifOriginal("2016:01:07 10:15:00")

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertEquals(setOf(MonthDay.of(1, 7)), repository.onThisDayIndex.value!!.entries.keys)
    }

    @Test
    fun `photos with no date anywhere stay out of the index, without falling back to DATE_ADDED`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"), row(3, 0L, name = null))

        repository.onThisDayIndex.test {
            assertNull(awaitItem())
            repository.refreshOnThisDayIndex()
            assertTrue(awaitItem()!!.isEmpty)

            advanceUntilIdle()

            // Nothing was added, so the index is not emitted a second time
            expectNoEvents()
        }
    }

    @Test
    fun `a second refresh reads no file again`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"))
        reader.tags[2] = exifOriginal("2016:01:07 10:15:00")
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        assertEquals(listOf(2L), reader.reads)

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertEquals(listOf(2L), reader.reads)
        assertEquals(1, repository.onThisDayIndex.value!!.totalPhotos)
    }

    @Test
    fun `a file that was changed is read again, and an undated file is not retried`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, modified = 100, name = "photo.jpg"), row(3, null, name = "other.jpg"))
        reader.tags[2] = exifOriginal("2016:01:07 10:15:00")
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        reader.reads.clear()

        FakeMediaProvider.rows = listOf(row(2, null, modified = 200, name = "photo.jpg"), row(3, null, name = "other.jpg"))
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        // 3 has no date but is cached as undated for this version of the file
        assertEquals(listOf(2L), reader.reads)
    }

    @Test
    fun `cached dates of photos that are gone are dropped`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"), row(3, null, name = "IMG_20160107_101500.jpg"))
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        assertEquals(setOf(2L, 3L), cache.entries.keys)

        FakeMediaProvider.rows = listOf(row(3, null, name = "IMG_20160107_101500.jpg"))
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertEquals(setOf(3L), cache.entries.keys)
    }

    // --- no blink: the first index already has what the cache knows ---

    @Test
    fun `with a warm cache the first published index already has the cached photos`() = runTest {
        FakeMediaProvider.rows = listOf(
            row(1, millis(LocalDate.of(2024, 3, 9))),
            row(2, null, name = "photo.jpg"),
            row(3, null, name = "IMG_20160309_101500.jpg"),
        )
        reader.tags[2] = exifOriginal("2016:03:09 09:00:00")
        // An earlier run of the app filled the cache
        repository(StandardTestDispatcher(testScheduler)).also {
            it.refreshOnThisDayIndex()
            advanceUntilIdle()
        }
        reader.reads.clear()

        // A new process: the cache is warm, the index is not
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.onThisDayIndex.test {
            assertNull(awaitItem())

            repository.refreshOnThisDayIndex()
            val first = awaitItem()!!
            advanceUntilIdle()

            assertEquals(3, first.totalPhotos)
            assertEquals(3, first.entries.getValue(MonthDay.of(3, 9)).count)
            // Nothing was missing, so the index is not emitted a second time and no file is read
            expectNoEvents()
        }
        assertTrue(reader.reads.isEmpty())
    }

    @Test
    fun `an index is never a subset of the one before it only because the pass has not run`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(
            row(1, millis(LocalDate.of(2024, 3, 9))),
            row(2, null, name = "photo.jpg"),
        )
        reader.tags[2] = exifOriginal("2016:03:09 09:00:00")

        repository.onThisDayIndex.test {
            assertNull(awaitItem())
            repository.refreshOnThisDayIndex()
            assertEquals(1, awaitItem()!!.totalPhotos)
            advanceUntilIdle()
            assertEquals(2, awaitItem()!!.totalPhotos)

            // A new undated photo shows up; the cached one must stay in the index while the new one is read
            FakeMediaProvider.rows += row(4, null, name = "IMG_20150309_101500.jpg")
            repository.refreshOnThisDayIndex()
            advanceUntilIdle()

            assertEquals(3, awaitItem()!!.totalPhotos)
            expectNoEvents()
        }
    }

    @Test
    fun `a file changed since it was cached keeps its old date in the index until it is read again`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, modified = 100, name = "photo.jpg"))
        reader.tags[2] = exifOriginal("2016:03:09 09:00:00")
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        FakeMediaProvider.rows = listOf(row(2, null, modified = 200, name = "photo.jpg"))
        val queriesBefore = FakeMediaProvider.queries

        repository.onThisDayIndex.test {
            assertEquals(1, awaitItem()!!.totalPhotos)

            repository.refreshOnThisDayIndex()
            advanceUntilIdle()

            // Read again, same date: nothing to publish, no second query
            expectNoEvents()
        }
        assertEquals(queriesBefore + 1, FakeMediaProvider.queries)
        assertEquals(200L, cache.entries.getValue(2).dateModified)
    }

    @Test
    fun `a changed file with another date moves the photo, and the index never loses it on the way`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, modified = 100, name = "photo.jpg"))
        reader.tags[2] = exifOriginal("2016:03:09 09:00:00")
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        FakeMediaProvider.rows = listOf(row(2, null, modified = 200, name = "photo.jpg"))
        reader.tags[2] = exifOriginal("2017:05:01 09:00:00")

        repository.onThisDayIndex.test {
            assertEquals(setOf(MonthDay.of(3, 9)), awaitItem()!!.entries.keys)

            repository.refreshOnThisDayIndex()
            advanceUntilIdle()

            assertEquals(setOf(MonthDay.of(5, 1)), awaitItem()!!.entries.keys)
            expectNoEvents()
        }
    }


    @Test
    fun `a photo is on the same day in getPhotosForDate and in the index`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(
            row(1, millis(LocalDate.of(2024, 10, 15)), extra = mapOf(MediaStore.Images.Media.SIZE to 10L)),
            row(
                2, null, name = "photo.jpg",
                extra = mapOf(
                    MediaStore.Images.Media.SIZE to 20L,
                    MediaStore.Images.Media.WIDTH to 3_000,
                    MediaStore.Images.Media.HEIGHT to 4_000,
                    MediaStore.Images.Media.ORIENTATION to 90,
                ),
            ),
            row(3, null, name = "no date.jpg", extra = mapOf(MediaStore.Images.Media.SIZE to 30L)),
        )
        reader.tags[2] = exifOriginal("2016:10:15 09:00:00")

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        val photos = repository.getPhotosForDate(today)

        assertEquals(listOf(2L, 1L), photos.map { it.id })
        // The photo dated from its EXIF has its shape too
        assertEquals(listOf(3_000, 4_000, 90), photos.first().let { listOf(it.width, it.height, it.orientation) })
        assertEquals(LocalDate.of(2016, 10, 15), photos.first().dateTaken)
        assertEquals(LocalDateTime.of(2016, 10, 15, 9, 0), photos.first().takenAt)
        assertEquals(100L, photos.first().dateModified)
        assertEquals(20L, photos.first().sizeBytes)
        val day = repository.onThisDayIndex.value!!.entries.getValue(MonthDay.of(10, 15))
        assertEquals(photos.size, day.count)
        assertEquals(2L, day.cover.id)
    }

    // --- getPhotosForDate never waits for the files ---

    @Test
    fun `getPhotosForDate answers at once and does not wait for the files to be read`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(
            row(1, millis(LocalDate.of(2024, 10, 15))),
            row(2, null, name = "photo.jpg"),
        )
        reader.tags[2] = exifOriginal("2016:10:15 09:00:00")

        val photos = repository.getPhotosForDate(today)

        // The dated photo is there, the undated one is not cached yet and no file was opened for this answer
        assertEquals(listOf(1L), photos.map { it.id })
        assertTrue(reader.reads.isEmpty())
    }

    @Test
    fun `the photos read in the background show up the next time`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"))
        reader.tags[2] = exifOriginal("2016:10:15 09:00:00")

        assertTrue(repository.getPhotosForDate(today).isEmpty())
        // The first call started the scan in the background
        advanceUntilIdle()
        assertEquals(listOf(2L), reader.reads)

        assertEquals(listOf(2L), repository.getPhotosForDate(today).map { it.id })
        assertEquals(listOf(2L), reader.reads)
    }

    @Test
    fun `the background scan is started once, not by every call`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"))

        repository.getPhotosForDate(today)
        repository.getPhotosForDate(today)
        repository.getPhotosForDate(today.minusDays(1))
        advanceUntilIdle()

        // 3 calls ask MediaStore for their own day, one more query is the scan
        assertEquals(4, FakeMediaProvider.queries)
    }

    @Test
    fun `no scan is started when the index is already loaded`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, millis(LocalDate.of(2024, 10, 15))))
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        val queries = FakeMediaProvider.queries

        repository.getPhotosForDate(today)
        advanceUntilIdle()

        assertEquals(queries + 1, FakeMediaProvider.queries)
    }

    @Test
    fun `getPhotosForDate leaves out an undated photo whose date is another day`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"))
        reader.tags[2] = exifOriginal("2016:10:16 09:00:00")
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertTrue(repository.getPhotosForDate(today).isEmpty())
    }

    @Test
    fun `a cached date is not used once the file changed`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, modified = 100, name = "photo.jpg"))
        reader.tags[2] = exifOriginal("2016:10:15 09:00:00")
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        FakeMediaProvider.rows = listOf(row(2, null, modified = 200, name = "photo.jpg"))

        assertTrue(repository.getPhotosForDate(today).isEmpty())
    }

    @Test
    fun `a cached date is not used once MediaStore has a date for the photo`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, modified = 100, name = "photo.jpg"))
        reader.tags[2] = exifOriginal("2016:10:15 09:00:00")
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        // MediaStore scanned the file since and dated it another day
        FakeMediaProvider.rows = listOf(row(2, millis(LocalDate.of(2020, 6, 1)), modified = 100, name = "photo.jpg"))

        // The fake answers every date query with its dated rows, so the photo is listed by its MediaStore date only:
        // never a second time, and never on the cached day
        val photos = repository.getPhotosForDate(today)
        assertEquals(listOf(LocalDate.of(2020, 6, 1)), photos.map { it.dateTaken })
    }

    @Test
    fun `a cached photo that was deleted is not listed`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"))
        reader.tags[2] = exifOriginal("2016:10:15 09:00:00")
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        FakeMediaProvider.rows = emptyList()

        assertTrue(repository.getPhotosForDate(today).isEmpty())
    }

    @Test
    fun `getPhotosForDate without permission throws and starts no scan`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        shadowOf(application).denyPermissions(Manifest.permission.READ_MEDIA_IMAGES)

        try {
            repository.getPhotosForDate(today)
            fail("expected PermissionDeniedException")
        } catch (e: PermissionDeniedException) {
            advanceUntilIdle()
            assertEquals(0, FakeMediaProvider.queries)
        }
    }

    // --- diagnostics ---

    @Test
    fun `diagnostics ask for type and folder in the same single query and log the breakdown`() = runTest {
        PerfLog.setEnabled(true)
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(
            row(
                1,
                taken = null,
                name = "IMG_20230601_101500.jpg",
                extra = mapOf(
                    MediaStore.Images.Media.MIME_TYPE to "image/jpeg",
                    MediaStore.Images.Media.RELATIVE_PATH to "Download/",
                ),
            ),
            row(
                2,
                taken = millis(LocalDate.of(2023, 6, 2)),
                extra = mapOf(
                    MediaStore.Images.Media.MIME_TYPE to "image/png",
                    MediaStore.Images.Media.RELATIVE_PATH to "DCIM/Camera/",
                ),
            ),
        )

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertEquals(1, FakeMediaProvider.queries)
        assertTrue(FakeMediaProvider.lastProjection.orEmpty().contains(MediaStore.Images.Media.MIME_TYPE))
        assertTrue(FakeMediaProvider.lastProjection.orEmpty().contains(MediaStore.Images.Media.RELATIVE_PATH))
        val summary = lines.single { it.contains("index: query=") }
        assertTrue(summary, summary.contains("rows=2"))
        assertTrue(summary, summary.contains("dateTakenNullOrZero=1"))
        assertTrue(lines.any { it.contains("by mime type") && it.contains("image/jpeg=1") && it.contains("image/png=1") })
        assertTrue(lines.any { it.contains("by relative path") && it.contains("Download/=1") && it.contains("DCIM/Camera/=1") })
        assertTrue(lines.any { it.contains("permissions") && it.contains("READ_MEDIA_IMAGES=granted") })
    }

    @Test
    fun `the raw EXIF tags of up to 10 undated photos are logged, with what the chain made of them`() = runTest {
        PerfLog.setEnabled(true)
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = (1L..12L).map { row(it, null, name = "photo$it.jpg") }
        reader.tags[1] = ExifDateTags(
            dateTimeOriginal = "2016:01:07 10:15:00",
            dateTime = "2016:01:08 10:15:00",
            offsetTimeOriginal = "+03:00",
        )

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        val samples = lines.filter { it.contains("dates: sample") }
        assertEquals(10, samples.size)
        val first = samples.first()
        assertTrue(first, first.contains("id=1 name=photo1.jpg"))
        assertTrue(first, first.contains("DateTimeOriginal=2016:01:07 10:15:00"))
        assertTrue(first, first.contains("OffsetTimeOriginal=+03:00"))
        assertTrue(first, first.contains("resolved=2016-01-07T10:15 offset=+03:00 via EXIF"))
        assertTrue(samples[1], samples[1].contains("unreadable"))
    }

    @Test
    fun `the resolution is summarised in the log with cache hits and what is still undated`() = runTest {
        PerfLog.setEnabled(true)
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(
            row(1, null, name = "IMG_20160107_101500.jpg"),
            row(2, null, name = "photo.jpg"),
        )
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        val first = lines.single { it.contains("dates: undated=") }
        assertTrue(first, first.contains("undated=2 phase1CacheHits=0 misses=2 stale=0 exif=0 gps=0 xmp=0 fileName=1 stillUndated=1"))
        lines.clear()

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        val summary = lines.single { it.contains("dates: undated=") }
        assertTrue(summary, summary.contains("undated=2 phase1CacheHits=2 misses=0 stale=0"))
        assertTrue(summary, summary.contains("indexChanged=false"))
    }

    @Test
    fun `the extra diagnostic columns are not requested when logging is off`() = runTest {
        PerfLog.setEnabled(false)
        val repository = repository(StandardTestDispatcher(testScheduler))

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertFalse(FakeMediaProvider.lastProjection.orEmpty().contains(MediaStore.Images.Media.MIME_TYPE))
        assertTrue(lines.isEmpty())
    }

    // --- whether the date of a photo has a time ---

    @Test
    fun `a photo dated by a file name without a time has no time, in the index and in the deck`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "IMG-20161015-WA0001.jpg"))

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        val photo = repository.getPhotosForDate(today).single()

        assertEquals(LocalDate.of(2016, 10, 15), photo.dateTaken)
        assertFalse(photo.hasTime)
    }

    @Test
    fun `a photo dated by a file name with a time keeps it`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "IMG_20161015_101500.jpg"))

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        val photo = repository.getPhotosForDate(today).single()

        assertEquals(LocalDateTime.of(2016, 10, 15, 10, 15, 0), photo.takenAt)
        assertTrue(photo.hasTime)
    }

    @Test
    fun `a photo dated by EXIF keeps its time, also a real midnight`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"))
        reader.tags[2] = exifOriginal("2016:10:15 00:00:00")

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        val photo = repository.getPhotosForDate(today).single()

        assertEquals(LocalDateTime.of(2016, 10, 15, 0, 0, 0), photo.takenAt)
        assertTrue(photo.hasTime)
    }

    @Test
    fun `a photo with DATE_TAKEN has a time, also at midnight`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, LocalDate.of(2024, 10, 15).atStartOfDay(zone).toInstant().toEpochMilli()))

        val photo = repository.getPhotosForDate(today).single()

        assertEquals(LocalDateTime.of(2024, 10, 15, 0, 0), photo.takenAt)
        assertTrue(photo.hasTime)
    }
}
