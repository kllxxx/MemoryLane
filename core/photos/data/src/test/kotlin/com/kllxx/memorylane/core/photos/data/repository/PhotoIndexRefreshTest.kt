package com.kllxx.memorylane.core.photos.data.repository

import android.Manifest
import android.app.Application
import android.provider.MediaStore
import com.kllxx.memorylane.core.common.PerfLog
import com.kllxx.memorylane.core.photos.data.geocoder.LocationGeocoder
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
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

    private fun repository(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        PhotoRepositoryImpl(application, geocoder, dispatcher, clock)

    private fun row(
        id: Long,
        taken: Long?,
        added: Long? = null,
        extra: Map<String, Any?> = emptyMap(),
    ) = mapOf<String, Any?>(
        MediaStore.Images.Media._ID to id,
        MediaStore.Images.Media.DATE_TAKEN to taken,
        MediaStore.Images.Media.DATE_ADDED to added,
    ) + extra

    private fun millis(date: LocalDate) = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `one refresh is exactly one query over every image, without any filter`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(row(1, millis(LocalDate.of(2024, 3, 9))))

        repository.refreshOnThisDayIndex()

        assertEquals(1, FakeMediaProvider.queries)
        assertNull(FakeMediaProvider.lastSelection)
        assertNull(FakeMediaProvider.lastSelectionArgs)
        assertEquals(
            listOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED),
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
    fun `rows without DATE_TAKEN use DATE_ADDED and rows with neither are skipped`() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        val addedSeconds = millis(LocalDate.of(2023, 6, 1)) / 1_000
        FakeMediaProvider.rows = listOf(
            row(1, taken = null, added = addedSeconds),
            row(2, taken = 0L, added = addedSeconds),
            row(3, taken = null, added = null),
        )

        repository.refreshOnThisDayIndex()

        val june = repository.onThisDayIndex.value!!.entries.getValue(MonthDay.of(6, 1))
        assertEquals(2, june.count)
        assertEquals(1, repository.onThisDayIndex.value!!.entries.size)
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

    @Test
    fun `diagnostics ask for type and folder in the same single query and log the breakdown`() = runTest {
        PerfLog.setEnabled(true)
        val repository = repository(StandardTestDispatcher(testScheduler))
        FakeMediaProvider.rows = listOf(
            row(
                1,
                taken = null,
                added = millis(LocalDate.of(2023, 6, 1)) / 1_000,
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

        assertEquals(1, FakeMediaProvider.queries)
        assertTrue(FakeMediaProvider.lastProjection.orEmpty().contains(MediaStore.Images.Media.MIME_TYPE))
        assertTrue(FakeMediaProvider.lastProjection.orEmpty().contains(MediaStore.Images.Media.RELATIVE_PATH))
        val summary = lines.single { it.contains("index: query=") }
        assertTrue(summary, summary.contains("rows=2"))
        assertTrue(summary, summary.contains("dateTakenNullOrZero=1"))
        assertTrue(summary, summary.contains("usedDateAdded=1"))
        assertTrue(summary, summary.contains("unusable=0"))
        assertTrue(lines.any { it.contains("by mime type") && it.contains("image/jpeg=1") && it.contains("image/png=1") })
        assertTrue(lines.any { it.contains("by relative path") && it.contains("Download/=1") && it.contains("DCIM/Camera/=1") })
        assertTrue(lines.any { it.contains("permissions") && it.contains("READ_MEDIA_IMAGES=granted") })
    }

    @Test
    fun `the extra diagnostic columns are not requested when logging is off`() = runTest {
        PerfLog.setEnabled(false)
        val repository = repository(StandardTestDispatcher(testScheduler))

        repository.refreshOnThisDayIndex()

        assertFalse(FakeMediaProvider.lastProjection.orEmpty().contains(MediaStore.Images.Media.MIME_TYPE))
        assertTrue(lines.isEmpty())
    }
}
