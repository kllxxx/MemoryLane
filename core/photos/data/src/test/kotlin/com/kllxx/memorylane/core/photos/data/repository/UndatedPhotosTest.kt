package com.kllxx.memorylane.core.photos.data.repository

import android.Manifest
import android.app.Application
import android.provider.MediaStore
import com.kllxx.memorylane.core.photos.data.date.ExifDateTags
import com.kllxx.memorylane.core.photos.data.date.PhotoDateResolver
import com.kllxx.memorylane.core.photos.data.date.ResolvedDate
import com.kllxx.memorylane.core.photos.data.date.UndatedPhoto
import com.kllxx.memorylane.core.photos.data.geocoder.LocationGeocoder
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.DateSource
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

/** The photos with no date anywhere: the ids in the index, and the deck of them. */
@RunWith(RobolectricTestRunner::class)
class UndatedPhotosTest {

    private val application: Application = RuntimeEnvironment.getApplication()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(today.atTime(12, 0).atZone(zone).toInstant(), zone)
    private val geocoder = mockk<LocationGeocoder>()
    private val reader = FakeExifDateReader()
    private val cache = FakePhotoDateCache()

    @Before
    fun setUp() {
        FakeMediaProvider.reset()
        // The cache was already looked through for this version of the chain: what the tests put in it stays
        cache.forgottenFor = PhotoDateResolver.DATE_CHAIN_VERSION
        Robolectric.buildContentProvider(FakeMediaProvider::class.java).create(MediaStore.AUTHORITY)
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    }

    private fun repository(dispatcher: CoroutineDispatcher) =
        PhotoRepositoryImpl(application, geocoder, dispatcher, clock, reader, cache, FakePhotoLocationCache())

    private fun millis(date: LocalDate) = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun row(id: Long, taken: Long?, modified: Long = 100, name: String? = null, added: Long = 1_000) = mapOf<String, Any?>(
        MediaStore.Images.Media._ID to id,
        MediaStore.Images.Media.DATE_TAKEN to taken,
        MediaStore.Images.Media.DATE_MODIFIED to modified,
        MediaStore.Images.Media.DISPLAY_NAME to name,
        MediaStore.Images.Media.DATE_ADDED to added,
        MediaStore.Images.Media.SIZE to id * 10,
    )

    private fun cachedUndated(id: Long, modified: Long = 100) {
        cache.entries[id] = ResolvedDate.undatedToCached(UndatedPhoto(id, modified, null, "image/jpeg"))
    }

    // --- OnThisDayIndex.undatedIds ---

    @Test
    fun `the photos left without a date after the files were read are in the index`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(
            row(1, millis(LocalDate.of(2024, 3, 9))),
            row(2, null, name = "3IHhA-xTIJo.jpg"),
            row(3, null, name = "img_1-37.webp"),
        )
        reader.tags[2] = ExifDateTags()
        reader.tags[3] = ExifDateTags()

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertEquals(setOf(2L, 3L), repository.onThisDayIndex.value!!.undatedIds)
    }

    @Test
    fun `a photo that got a date from its file is not undated`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(
            row(2, null, name = "3IHhA-xTIJo.jpg"),
            row(3, null, name = "IMG_20210622_164736_130.jpg"),
            row(4, null, name = "photo.jpg"),
        )
        reader.tags[2] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertEquals(setOf(4L), repository.onThisDayIndex.value!!.undatedIds)
    }

    @Test
    fun `a photo whose file has not been read yet is not undated, it may still get a date`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"))

        repository.refreshOnThisDayIndex()

        assertTrue(repository.onThisDayIndex.value!!.undatedIds.isEmpty())
    }

    @Test
    fun `photos the cache knows to be undated are in the first index of a refresh`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"), row(3, null, name = "other.jpg"))
        cachedUndated(2)

        repository.refreshOnThisDayIndex()

        assertEquals(setOf(2L), repository.onThisDayIndex.value!!.undatedIds)
    }

    @Test
    fun `a pass that dates nothing still emits the index, once, for the undated set`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"))
        reader.tags[2] = ExifDateTags()
        val seen = mutableListOf<Set<Long>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.onThisDayIndex.collect { index -> if (index != null) seen += index.undatedIds }
        }

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertEquals(listOf(emptySet(), setOf(2L)), seen)
    }

    @Test
    fun `a second refresh with nothing new does not emit again`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"))
        reader.tags[2] = ExifDateTags()
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        val first = repository.onThisDayIndex.value

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertEquals(first, repository.onThisDayIndex.value)
        assertEquals(setOf(2L), repository.onThisDayIndex.value!!.undatedIds)
    }

    @Test
    fun `a photo that was undated and whose file now has a date leaves the undated ids`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, modified = 100, name = "photo.jpg"))
        cachedUndated(2, modified = 100)
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()
        assertEquals(setOf(2L), repository.onThisDayIndex.value!!.undatedIds)

        // The file was edited and now carries a date
        FakeMediaProvider.rows = listOf(row(2, null, modified = 200, name = "photo.jpg"))
        reader.tags[2] = ExifDateTags(dateTimeOriginal = "2016:01:07 10:15:00")
        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertTrue(repository.onThisDayIndex.value!!.undatedIds.isEmpty())
    }

    @Test
    fun `a photo that was dated by its name and whose file changed to no date is undated now`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, modified = 200, name = "photo.jpg"))
        cache.entries[2] = ResolvedDate(
            java.time.LocalDateTime.of(2016, 1, 7, 10, 15), null, DateSource.FILENAME,
        ).toCached(UndatedPhoto(2, 100, "photo.jpg", "image/jpeg"))
        reader.tags[2] = ExifDateTags()

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertEquals(setOf(2L), repository.onThisDayIndex.value!!.undatedIds)
    }

    @Test
    fun `a photo with a MediaStore date is never undated`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, millis(LocalDate.of(2024, 3, 9))))
        cachedUndated(1)

        repository.refreshOnThisDayIndex()
        advanceUntilIdle()

        assertTrue(repository.onThisDayIndex.value!!.undatedIds.isEmpty())
    }

    // --- getUndatedPhotos ---

    @Test
    fun `the undated deck is by DATE_ADDED and then id, and has no date`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(
            row(5, null, added = 3_000),
            row(7, null, added = 1_000),
            row(6, null, added = 1_000),
            row(8, null, added = 2_000),
        )
        listOf(5L, 6L, 7L, 8L).forEach { cachedUndated(it) }

        val photos = repository.getUndatedPhotos()

        assertEquals(listOf(6L, 7L, 8L, 5L), photos.map { it.id })
        assertTrue(photos.all { it.takenAt == null })
        assertNull(photos.first().dateTaken)
        assertEquals(60L, photos.first().sizeBytes)
        assertEquals(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath("6").build(),
            photos.first().uri,
        )
    }

    @Test
    fun `DATE_ADDED only orders the deck, it is not asked for`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(5, null))
        cachedUndated(5)

        repository.getUndatedPhotos()

        assertTrue(FakeMediaProvider.lastProjection!!.none { it == MediaStore.Images.Media.DATE_ADDED })
    }

    @Test
    fun `photos with a date of MediaStore are not in the undated deck`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, millis(LocalDate.of(2024, 3, 9))), row(2, null))
        cachedUndated(1)
        cachedUndated(2)

        assertEquals(listOf(2L), repository.getUndatedPhotos().map { it.id })
    }

    @Test
    fun `photos the chain dated are not in the undated deck`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, null), row(2, null))
        cache.entries[1] = ResolvedDate(java.time.LocalDateTime.of(2016, 1, 7, 10, 15), null, DateSource.EXIF)
            .toCached(UndatedPhoto(1, 100, null, null))
        cachedUndated(2)

        assertEquals(listOf(2L), repository.getUndatedPhotos().map { it.id })
    }

    @Test
    fun `photos the chain has not reached are not in the undated deck`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, null), row(2, null))
        cachedUndated(2)

        assertEquals(listOf(2L), repository.getUndatedPhotos().map { it.id })
    }

    @Test
    fun `a photo whose file changed since the chain looked is not in the undated deck`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, null, modified = 200), row(2, null, modified = 100))
        cachedUndated(1, modified = 100)
        cachedUndated(2, modified = 100)

        assertEquals(listOf(2L), repository.getUndatedPhotos().map { it.id })
    }

    @Test
    fun `no undated photos is an empty deck`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, millis(LocalDate.of(2024, 3, 9))))

        assertTrue(repository.getUndatedPhotos().isEmpty())
    }

    @Test
    fun `without the permission the deck is not an empty one, it is an error`() = runTest {
        shadowOf(application).denyPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, null))

        try {
            repository.getUndatedPhotos()
            fail("expected PermissionDeniedException")
        } catch (e: PermissionDeniedException) {
            // expected
        }
    }

    @Test
    fun `the photos of the undated deck are not photos of any day`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(2, null, name = "photo.jpg"))
        cachedUndated(2)

        assertTrue(repository.getPhotosForDate(LocalDate.of(2026, 10, 15)).isEmpty())
        assertEquals(listOf(2L), repository.getUndatedPhotos().map { it.id })
    }
}
