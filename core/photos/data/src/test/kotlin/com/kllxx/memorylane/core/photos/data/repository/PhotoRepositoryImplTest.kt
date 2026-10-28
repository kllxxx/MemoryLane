package com.kllxx.memorylane.core.photos.data.repository

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import com.kllxx.memorylane.core.photos.data.geocoder.GeocodeResult
import com.kllxx.memorylane.core.photos.data.geocoder.LocationGeocoder
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoDate
import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoLocation
import com.kllxx.memorylane.core.photos.domain.model.DateSource
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.model.PhotoLocation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.time.ZoneId
import kotlinx.coroutines.flow.toList
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

    // Remembers the bundle, then lets the default implementation hand its selection to the query above
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?,
    ): Cursor? {
        lastQueryArgs = queryArgs
        return super.query(uri, projection, queryArgs, cancellationSignal)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        var rows: List<Map<String, Any?>> = emptyList()
        var lastQueryArgs: Bundle? = null
        var queries = 0
        var lastSelectionArgs: List<String>? = null
        var lastSelection: String? = null
        var lastProjection: List<String>? = null

        fun reset() {
            rows = emptyList()
            lastQueryArgs = null
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
    private val dateCache = FakePhotoDateCache()
    private val locationCache = FakePhotoLocationCache()
    private lateinit var repository: PhotoRepositoryImpl

    @Before
    fun setUp() {
        FakeMediaProvider.reset()
        Robolectric.buildContentProvider(FakeMediaProvider::class.java).create(MediaStore.AUTHORITY)
        repository = PhotoRepositoryImpl(
            application, geocoder, UnconfinedTestDispatcher(), clock, FakeExifDateReader(), dateCache, locationCache,
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
                MediaStore.Images.Media.DATE_MODIFIED to 1_234L,
                MediaStore.Images.Media.SIZE to 2_048L,
            ),
        )

        val photos = repository.getPhotosForDate(date)

        assertEquals(1, photos.size)
        val photo = photos.single()
        assertEquals(7L, photo.id)
        assertEquals(2_048L, photo.sizeBytes)
        assertEquals(taken.atZone(zone).toLocalDate(), photo.dateTaken)
        assertEquals(date.minusYears(1).atTime(10, 30), photo.takenAt)
        assertEquals(1_234L, photo.dateModified)
        assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath("7").build(), photo.uri)
        assertNull(photo.location)
    }

    @Test
    fun `a photo carries the size and the orientation MediaStore stores`() = runTest {
        grantMedia()
        FakeMediaProvider.rows = listOf(
            datedRow(7, date.minusYears(1).atTime(10, 30)) + mapOf(
                MediaStore.Images.Media.WIDTH to 4_000,
                MediaStore.Images.Media.HEIGHT to 3_000,
                MediaStore.Images.Media.ORIENTATION to 90,
            ),
        )

        val photo = repository.getPhotosForDate(date).single()

        assertEquals(4_000, photo.width)
        assertEquals(3_000, photo.height)
        assertEquals(90, photo.orientation)
    }

    @Test
    fun `a photo MediaStore has no size for reads as 0, never as an error`() = runTest {
        grantMedia()
        FakeMediaProvider.rows = listOf(
            datedRow(7, date.minusYears(1).atTime(10, 30)) + mapOf(
                MediaStore.Images.Media.WIDTH to null,
                MediaStore.Images.Media.HEIGHT to null,
                MediaStore.Images.Media.ORIENTATION to null,
            ),
        )

        val photo = repository.getPhotosForDate(date).single()

        assertEquals(0, photo.width)
        assertEquals(0, photo.height)
        assertEquals(0, photo.orientation)
    }

    @Test
    fun `the photo query asks for the size and the orientation`() = runTest {
        grantMedia()
        repository.refreshOnThisDayIndex()

        repository.getPhotosForDate(date)

        val projection = FakeMediaProvider.lastProjection.orEmpty()
        assertTrue(MediaStore.Images.Media.WIDTH in projection)
        assertTrue(MediaStore.Images.Media.HEIGHT in projection)
        assertTrue(MediaStore.Images.Media.ORIENTATION in projection)
    }

    @Test
    fun `the query asks for this day in earlier years only`() = runTest {
        grantMedia()
        // With the index loaded, the only query left is the one of the day
        repository.refreshOnThisDayIndex()
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

    // --- deck order ---

    private fun datedRow(id: Long, taken: LocalDateTime, modified: Long = 100) = mapOf<String, Any?>(
        MediaStore.Images.Media._ID to id,
        MediaStore.Images.Media.DATE_TAKEN to taken.atZone(zone).toInstant().toEpochMilli(),
        MediaStore.Images.Media.DATE_MODIFIED to modified,
        MediaStore.Images.Media.SIZE to 1L,
    )

    @Test
    fun `the deck is sorted by capture time, oldest first, and the id breaks a tie`() = runTest {
        grantMedia()
        val earlier = date.minusYears(2).atTime(10, 0)
        val later = date.minusYears(1).atTime(8, 0)
        FakeMediaProvider.rows = listOf(
            datedRow(5, later),
            datedRow(9, earlier),
            datedRow(3, earlier),
        )

        assertEquals(listOf(3L, 9L, 5L), repository.getPhotosForDate(date).map { it.id })
    }

    @Test
    fun `photos of the same day are told apart by the time of day, not only the date`() = runTest {
        grantMedia()
        val day = date.minusYears(1)
        FakeMediaProvider.rows = listOf(
            datedRow(1, day.atTime(21, 15)),
            datedRow(2, day.atTime(7, 5)),
        )

        val photos = repository.getPhotosForDate(date)

        assertEquals(listOf(2L, 1L), photos.map { it.id })
        assertEquals(listOf(day.atTime(7, 5), day.atTime(21, 15)), photos.map { it.takenAt })
    }

    // --- locations ---

    private val localeTag get() = Locale.getDefault().toLanguageTag()

    private fun photo(id: Long = 7, modified: Long = 100) = Photo(
        id = id,
        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build(),
        takenAt = date.minusYears(1).atTime(10, 0),
        sizeBytes = 1,
        dateModified = modified,
    )

    @Suppress("DEPRECATION")
    private fun coordinates(latitude: Double?, longitude: Double?) = mapOf(
        MediaStore.Images.ImageColumns.LATITUDE to latitude,
        MediaStore.Images.ImageColumns.LONGITUDE to longitude,
    )

    @Test
    @Config(sdk = [28])
    fun `resolveLocations geocodes the MediaStore coordinates below API 29 and remembers the place`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(48.85, 2.35))
        coEvery { geocoder.getLocation(48.85, 2.35) } returns GeocodeResult.Found("Paris, France")

        val found = repository.resolveLocations(listOf(photo())).toList()

        assertEquals(listOf(PhotoLocation(7, "Paris, France")), found)
        assertEquals(CachedPhotoLocation(7, 100, localeTag, "Paris, France"), locationCache.entries[7L])
    }

    @Test
    @Config(sdk = [28])
    fun `resolveLocations treats 0 0 as no GPS, caches that and does not geocode`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(0.0, 0.0))

        val found = repository.resolveLocations(listOf(photo())).toList()

        assertEquals(listOf(PhotoLocation(7, null)), found)
        assertEquals(CachedPhotoLocation(7, 100, localeTag, null), locationCache.entries[7L])
        coVerify(exactly = 0) { geocoder.getLocation(any(), any()) }
    }

    @Test
    @Config(sdk = [28])
    fun `resolveLocations caches a photo without coordinates as having no place`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(null, null))

        val found = repository.resolveLocations(listOf(photo())).toList()

        assertEquals(listOf(PhotoLocation(7, null)), found)
        assertEquals(CachedPhotoLocation(7, 100, localeTag, null), locationCache.entries[7L])
        coVerify(exactly = 0) { geocoder.getLocation(any(), any()) }
    }

    @Test
    @Config(sdk = [28])
    fun `resolveLocations caches a place the geocoder does not know as null`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(48.85, 2.35))
        coEvery { geocoder.getLocation(any(), any()) } returns GeocodeResult.NoResult

        val found = repository.resolveLocations(listOf(photo())).toList()

        assertEquals(listOf(PhotoLocation(7, null)), found)
        assertEquals(CachedPhotoLocation(7, 100, localeTag, null), locationCache.entries[7L])
    }

    @Test
    @Config(sdk = [28])
    fun `a geocoder failure is neither emitted nor cached, so the next visit tries again`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(48.85, 2.35))
        coEvery { geocoder.getLocation(any(), any()) } returns GeocodeResult.Failure

        val found = repository.resolveLocations(listOf(photo())).toList()

        assertTrue(found.isEmpty())
        assertTrue(locationCache.entries.isEmpty())
    }

    @Test
    @Config(sdk = [28])
    fun `a failure on one photo does not stop the next one`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(48.85, 2.35))
        coEvery { geocoder.getLocation(any(), any()) } returnsMany
            listOf(GeocodeResult.Failure, GeocodeResult.Found("Paris, France"))

        val found = repository.resolveLocations(listOf(photo(1), photo(2))).toList()

        assertEquals(listOf(PhotoLocation(2, "Paris, France")), found)
    }

    @Test
    fun `a remembered place is emitted without querying MediaStore or geocoding`() = runTest {
        locationCache.entries[7] = CachedPhotoLocation(7, 100, localeTag, "Berlin, Germany")
        locationCache.entries[8] = CachedPhotoLocation(8, 100, localeTag, null)

        val found = repository.resolveLocations(listOf(photo(7), photo(8))).toList()

        assertEquals(listOf(PhotoLocation(7, "Berlin, Germany"), PhotoLocation(8, null)), found)
        assertEquals(0, FakeMediaProvider.queries)
        coVerify(exactly = 0) { geocoder.getLocation(any(), any()) }
    }

    @Test
    @Config(sdk = [28])
    fun `a place remembered for another DATE_MODIFIED is looked up again`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(48.85, 2.35))
        locationCache.entries[7] = CachedPhotoLocation(7, 99, localeTag, "Old, Place")
        coEvery { geocoder.getLocation(any(), any()) } returns GeocodeResult.Found("Paris, France")

        val found = repository.resolveLocations(listOf(photo(7, modified = 100))).toList()

        assertEquals(listOf(PhotoLocation(7, "Paris, France")), found)
        assertEquals(100L, locationCache.entries[7L]?.dateModified)
    }

    @Test
    @Config(sdk = [28])
    fun `a place remembered in another language is looked up again`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(48.85, 2.35))
        locationCache.entries[7] = CachedPhotoLocation(7, 100, "xx-YY", "Paris, France")
        coEvery { geocoder.getLocation(any(), any()) } returns GeocodeResult.Found("Париж, Франция")

        val found = repository.resolveLocations(listOf(photo())).toList()

        assertEquals(listOf(PhotoLocation(7, "Париж, Франция")), found)
        assertEquals(localeTag, locationCache.entries[7L]?.locale)
    }

    @Test
    @Config(sdk = [28])
    fun `remembered places come first, then the others one by one in the given order`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(48.85, 2.35))
        locationCache.entries[2] = CachedPhotoLocation(2, 100, localeTag, "Cached, Place")
        coEvery { geocoder.getLocation(any(), any()) } returnsMany
            listOf(GeocodeResult.Found("First, Place"), GeocodeResult.Found("Third, Place"))

        val found = repository.resolveLocations(listOf(photo(1), photo(2), photo(3))).toList()

        assertEquals(
            listOf(PhotoLocation(2, "Cached, Place"), PhotoLocation(1, "First, Place"), PhotoLocation(3, "Third, Place")),
            found,
        )
    }

    @Test
    fun `without ACCESS_MEDIA_LOCATION nothing is read or geocoded, and nothing is remembered`() = runTest {
        shadowOf(application).denyPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)

        val found = repository.resolveLocations(listOf(photo())).toList()

        // Granting the permission later must still find the place
        assertEquals(listOf(PhotoLocation(7, null)), found)
        assertTrue(locationCache.entries.isEmpty())
        assertEquals(0, FakeMediaProvider.queries)
        coVerify(exactly = 0) { geocoder.getLocation(any(), any()) }
    }

    @Test
    fun `a photo whose original file cannot be read has no place`() = runTest {
        // The fake provider has no file behind the photo, so the EXIF read fails and yields no coordinates
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)

        val found = repository.resolveLocations(listOf(photo())).toList()

        assertEquals(listOf(PhotoLocation(7, null)), found)
        coVerify(exactly = 0) { geocoder.getLocation(any(), any()) }
    }

    @Test
    @Config(sdk = [28])
    fun `a cache that cannot be written does not lose the place`() = runTest {
        FakeMediaProvider.rows = listOf(coordinates(48.85, 2.35))
        coEvery { geocoder.getLocation(any(), any()) } returns GeocodeResult.Found("Paris, France")
        locationCache.failWrites = true

        val found = repository.resolveLocations(listOf(photo())).toList()

        assertEquals(listOf(PhotoLocation(7, "Paris, France")), found)
    }

    @Test
    fun `no photos, no locations`() = runTest {
        assertTrue(repository.resolveLocations(emptyList()).toList().isEmpty())
    }

    // --- trashed photos ---

    private fun trashedRow(id: Long, taken: LocalDateTime?, modified: Long = 100, size: Long = 10) = mapOf<String, Any?>(
        MediaStore.Images.Media._ID to id,
        MediaStore.Images.Media.DATE_TAKEN to taken?.atZone(zone)?.toInstant()?.toEpochMilli(),
        MediaStore.Images.Media.DATE_MODIFIED to modified,
        MediaStore.Images.Media.SIZE to size,
    )

    @Test
    @Config(sdk = [29])
    fun `there is no trash below API 30, so no trashed photos and no query`() = runTest {
        grantMedia()
        FakeMediaProvider.rows = listOf(trashedRow(5, date.minusYears(1).atTime(10, 0)))

        assertTrue(repository.getTrashedPhotos(listOf(5L)).isEmpty())
        assertEquals(0, FakeMediaProvider.queries)
    }

    @Test
    fun `no ids, no query`() = runTest {
        grantMedia()

        assertTrue(repository.getTrashedPhotos(emptyList()).isEmpty())
        assertEquals(0, FakeMediaProvider.queries)
    }

    @Test
    fun `getTrashedPhotos throws PermissionDeniedException without permission and never queries`() = runTest {
        denyMedia()
        try {
            repository.getTrashedPhotos(listOf(5L))
            fail("expected PermissionDeniedException")
        } catch (e: PermissionDeniedException) {
            assertEquals(0, FakeMediaProvider.queries)
        }
    }

    @Test
    @Config(sdk = [30])
    fun `getTrashedPhotos asks for trashed items only, by id`() = runTest {
        grantMedia()
        shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        FakeMediaProvider.rows = listOf(trashedRow(5, date.minusYears(1).atTime(10, 0)))

        repository.getTrashedPhotos(listOf(5L, 6L))

        val args = FakeMediaProvider.lastQueryArgs!!
        assertEquals(MediaStore.MATCH_ONLY, args.getInt(MediaStore.QUERY_ARG_MATCH_TRASHED))
        assertTrue(args.getString(ContentResolver.QUERY_ARG_SQL_SELECTION)!!.startsWith("${MediaStore.Images.Media._ID} IN"))
        assertEquals(listOf("5", "6"), args.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)!!.toList())
    }

    @Test
    @Config(sdk = [30])
    fun `getTrashedPhotos returns the requested photos oldest first, with time and size`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        val old = date.minusYears(3).atTime(9, 30)
        val recent = date.minusYears(1).atTime(18, 45)
        FakeMediaProvider.rows = listOf(
            trashedRow(5, recent, modified = 11, size = 500),
            trashedRow(6, old, modified = 22, size = 700),
            // Not asked for: a query that answers with more rows must not add photos
            trashedRow(9, old),
        )

        val photos = repository.getTrashedPhotos(listOf(5L, 6L))

        assertEquals(listOf(6L, 5L), photos.map { it.id })
        assertEquals(listOf(old, recent), photos.map { it.takenAt })
        assertEquals(listOf(22L, 11L), photos.map { it.dateModified })
        assertEquals(listOf(700L, 500L), photos.map { it.sizeBytes })
        assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath("6").build(), photos.first().uri)
    }

    @Test
    @Config(sdk = [30])
    fun `a trashed photo without DATE_TAKEN takes the resolved date, else the modification time`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        val resolved = LocalDateTime.of(2015, 6, 1, 12, 0)
        dateCache.entries[5] = CachedPhotoDate(
            id = 5,
            dateModified = 100,
            takenLocalEpochSecond = resolved.toEpochSecond(ZoneOffset.UTC),
            offsetSeconds = null,
            source = DateSource.EXIF,
        )
        val modified = date.minusYears(1).atTime(8, 0).atZone(zone).toEpochSecond()
        FakeMediaProvider.rows = listOf(
            trashedRow(5, taken = null),
            trashedRow(6, taken = null, modified = modified),
        )

        val photos = repository.getTrashedPhotos(listOf(5L, 6L)).associateBy { it.id }

        assertEquals(resolved, photos.getValue(5).takenAt)
        assertEquals(date.minusYears(1).atTime(8, 0), photos.getValue(6).takenAt)
    }

    @Test
    @Config(sdk = [30])
    fun `a trashed photo carries its size and orientation`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        FakeMediaProvider.rows = listOf(
            trashedRow(5, date.minusYears(1).atTime(10, 0)) + mapOf(
                MediaStore.Images.Media.WIDTH to 1_920,
                MediaStore.Images.Media.HEIGHT to 1_080,
                MediaStore.Images.Media.ORIENTATION to 270,
            ),
        )

        val photo = repository.getTrashedPhotos(listOf(5L)).single()

        assertEquals(1_920, photo.width)
        assertEquals(1_080, photo.height)
        assertEquals(270, photo.orientation)
    }

    @Test
    @Config(sdk = [30])
    fun `a trashed photo a size is missing for reads as 0`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        FakeMediaProvider.rows = listOf(trashedRow(5, date.minusYears(1).atTime(10, 0)))

        val photo = repository.getTrashedPhotos(listOf(5L)).single()

        assertEquals(0, photo.width)
        assertEquals(0, photo.height)
    }

    @Test
    @Config(sdk = [30])
    fun `a trashed photo that has left the trash is missing from the result`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        FakeMediaProvider.rows = listOf(trashedRow(5, date.minusYears(1).atTime(10, 0)))

        assertEquals(listOf(5L), repository.getTrashedPhotos(listOf(5L, 6L)).map { it.id })
    }

    @Test
    @Config(sdk = [30])
    fun `a trashed photo dated by a file name without a time has no time, any other has one`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        fun cached(id: Long, hasTime: Boolean) = CachedPhotoDate(
            id = id,
            dateModified = 100,
            takenLocalEpochSecond = LocalDateTime.of(2015, 6, 1, 12, 0).toEpochSecond(ZoneOffset.UTC),
            offsetSeconds = null,
            source = DateSource.FILENAME,
            hasTime = hasTime,
        )
        dateCache.entries[5] = cached(5, hasTime = false)
        dateCache.entries[6] = cached(6, hasTime = true)
        FakeMediaProvider.rows = listOf(
            trashedRow(5, taken = null),
            trashedRow(6, taken = null),
            trashedRow(7, taken = date.minusYears(1).atStartOfDay()),
            trashedRow(8, taken = null, modified = date.minusYears(1).atStartOfDay(zone).toEpochSecond()),
        )

        val photos = repository.getTrashedPhotos(listOf(5L, 6L, 7L, 8L)).associateBy { it.id }

        assertEquals(false, photos.getValue(5).hasTime)
        assertEquals(true, photos.getValue(6).hasTime)
        // DATE_TAKEN at midnight and the modification time are real times
        assertEquals(true, photos.getValue(7).hasTime)
        assertEquals(true, photos.getValue(8).hasTime)
    }
}
