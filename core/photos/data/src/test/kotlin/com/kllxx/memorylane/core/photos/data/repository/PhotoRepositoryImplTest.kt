package com.kllxx.memorylane.core.photos.data.repository

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import com.kllxx.memorylane.core.photos.data.geocoder.LocationGeocoder
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.annotation.Config

/** Fake MediaStore: answers every query with [rows] and remembers what it was asked. */
class FakeMediaProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        queries++
        lastSelectionArgs = selectionArgs?.toList()
        lastSelection = selection
        lastProjection = projection?.toList()
        val columns = projection.orEmpty()
        val cursor = MatrixCursor(columns)
        // MediaStore is not simulated, only the two filters the repository relies on to keep undated photos apart
        val answered = when {
            selection?.contains("${MediaStore.Images.Media.DATE_TAKEN} >=") == true ->
                rows.filter { it[MediaStore.Images.Media.DATE_TAKEN] != null }
            selection?.startsWith("${MediaStore.Images.Media._ID} IN") == true ->
                rows.filter { it[MediaStore.Images.Media._ID].toString() in selectionArgs.orEmpty() }
            else -> rows
        }
        answered.forEach { row -> cursor.addRow(columns.map { column -> row[column] }) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        var rows: List<Map<String, Any?>> = emptyList()
        var queries = 0
        var lastSelectionArgs: List<String>? = null
        var lastSelection: String? = null
        var lastProjection: List<String>? = null

        fun reset() {
            rows = emptyList()
            queries = 0
            lastSelectionArgs = null
            lastSelection = null
            lastProjection = null
        }
    }
}

@RunWith(RobolectricTestRunner::class)
class PhotoRepositoryImplTest {

    private val application: Application = RuntimeEnvironment.getApplication()
    private val date = LocalDate.of(2026, 10, 15)
    private val zone = ZoneId.systemDefault()
    private val clock = Clock.fixed(date.atTime(12, 0).atZone(zone).toInstant(), zone)

    // Locations are only looked up per photo; any unexpected geocoding call fails the test
    private val geocoder = mockk<LocationGeocoder>()
    private lateinit var repository: PhotoRepositoryImpl

    @Before
    fun setUp() {
        FakeMediaProvider.reset()
        Robolectric.buildContentProvider(FakeMediaProvider::class.java).create(MediaStore.AUTHORITY)
        repository = PhotoRepositoryImpl(
            application, geocoder, UnconfinedTestDispatcher(), clock, FakeExifDateReader(), FakePhotoDateCache(),
        )
    }

    private fun grantMedia() = shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)

    private fun denyMedia() = shadowOf(application).denyPermissions(Manifest.permission.READ_MEDIA_IMAGES)

    @Test
    fun `hasMediaPermission follows READ_MEDIA_IMAGES on API 33 and above`() {
        denyMedia()
        assertFalse(repository.hasMediaPermission())
        grantMedia()
        assertTrue(repository.hasMediaPermission())
    }

    @Test
    @Config(sdk = [30])
    fun `hasMediaPermission follows READ_EXTERNAL_STORAGE below API 33`() {
        shadowOf(application).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertFalse(repository.hasMediaPermission())
        shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertTrue(repository.hasMediaPermission())
    }

    @Test
    fun `getPhotosForDate throws PermissionDeniedException without permission and never queries`() = runTest {
        denyMedia()
        try {
            repository.getPhotosForDate(date)
            fail("expected PermissionDeniedException")
        } catch (e: PermissionDeniedException) {
            assertEquals(0, FakeMediaProvider.queries)
        }
    }


    @Test
    fun `an empty cursor with permission is an empty day, not an error`() = runTest {
        grantMedia()
        assertTrue(repository.getPhotosForDate(date).isEmpty())
        assertTrue(FakeMediaProvider.queries > 0)
    }


    @Test
    fun `a row from the cursor becomes a photo`() = runTest {
        grantMedia()
        val taken = date.minusYears(1).atTime(10, 30).atZone(zone).toInstant()
        FakeMediaProvider.rows = listOf(
            mapOf(
                MediaStore.Images.Media._ID to 7L,
                MediaStore.Images.Media.DATE_TAKEN to taken.toEpochMilli(),
                MediaStore.Images.Media.SIZE to 2_048L,
            ),
        )

        val photos = repository.getPhotosForDate(date)

        assertEquals(1, photos.size)
        val photo = photos.single()
        assertEquals(7L, photo.id)
        assertEquals(2_048L, photo.sizeBytes)
        assertEquals(taken.atZone(zone).toLocalDate(), photo.dateTaken)
        assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath("7").build(), photo.uri)
        assertNull(photo.location)
    }

    @Test
    fun `the query asks for this day in earlier years only`() = runTest {
        grantMedia()
        repository.getPhotosForDate(date)

        val args = FakeMediaProvider.lastSelectionArgs.orEmpty().map { it.toLong() }
        // One [start, end) pair per earlier year, newest first, and none for the current year
        assertTrue(args.isNotEmpty() && args.size % 2 == 0)
        assertEquals(date.minusYears(1), Instant.ofEpochMilli(args.first()).atZone(zone).toLocalDate())
        val startYears = args.chunked(2).map { Instant.ofEpochMilli(it.first()).atZone(zone).year }
        assertFalse(date.year in startYears)
    }

    @Test
    fun `photos from the date queries carry no location`() = runTest {
        grantMedia()
        FakeMediaProvider.rows = listOf(
            mapOf(
                MediaStore.Images.Media._ID to 1L,
                MediaStore.Images.Media.DATE_TAKEN to date.minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                MediaStore.Images.Media.SIZE to 1L,
            ),
        )
        assertNull(repository.getPhotosForDate(date).single().location)
        coVerify(exactly = 0) { geocoder.getLocation(any(), any()) }
    }

    @Suppress("DEPRECATION")
    private fun coordinates(latitude: Double?, longitude: Double?) = mapOf(
        MediaStore.Images.ImageColumns.LATITUDE to latitude,
        MediaStore.Images.ImageColumns.LONGITUDE to longitude,
    )

    @Test
    @Config(sdk = [28])
    fun `getPhotoLocation geocodes the MediaStore coordinates below API 29`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(48.85, 2.35))
        coEvery { geocoder.getLocation(48.85, 2.35) } returns "Paris, Rue de Rivoli"

        assertEquals("Paris, Rue de Rivoli", repository.getPhotoLocation(7))
    }

    @Test
    @Config(sdk = [28])
    fun `getPhotoLocation treats 0 0 as no location and does not geocode`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(0.0, 0.0))

        assertNull(repository.getPhotoLocation(7))
        coVerify(exactly = 0) { geocoder.getLocation(any(), any()) }
    }

    @Test
    @Config(sdk = [28])
    fun `getPhotoLocation is null for a photo without coordinates`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(null, null))

        assertNull(repository.getPhotoLocation(7))
        coVerify(exactly = 0) { geocoder.getLocation(any(), any()) }
    }

    @Test
    @Config(sdk = [28])
    fun `getPhotoLocation is null when the geocoder finds nothing`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(48.85, 2.35))
        coEvery { geocoder.getLocation(any(), any()) } returns null

        assertNull(repository.getPhotoLocation(7))
    }

    @Test
    @Config(sdk = [28])
    fun `getPhotoLocation never throws`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(48.85, 2.35))
        coEvery { geocoder.getLocation(any(), any()) } throws IllegalStateException("boom")

        assertNull(repository.getPhotoLocation(7))
    }

    @Test
    fun `getPhotoLocation without ACCESS_MEDIA_LOCATION does not read the photo or geocode`() = runTest {
        shadowOf(application).denyPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)

        assertNull(repository.getPhotoLocation(7))
        assertEquals(0, FakeMediaProvider.queries)
        coVerify(exactly = 0) { geocoder.getLocation(any(), any()) }
    }

    @Test
    fun `getPhotoLocation is null when the original file cannot be read`() = runTest {
        // The fake provider has no file behind the photo, so the EXIF read fails and yields no location
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)

        assertNull(repository.getPhotoLocation(7))
        coVerify(exactly = 0) { geocoder.getLocation(any(), any()) }
    }
}
