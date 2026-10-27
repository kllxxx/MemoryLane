package com.kllxx.memorylane.core.photos.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.kllxx.memorylane.core.common.PerfLog
import com.kllxx.memorylane.core.photos.data.PhotosConstants
import com.kllxx.memorylane.core.photos.data.di.IoDispatcher
import com.kllxx.memorylane.core.photos.data.geocoder.LocationGeocoder
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.DayCover
import com.kllxx.memorylane.core.photos.domain.model.DayMemories
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PhotoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geocoder: LocationGeocoder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : PhotoRepository {

    private val resolver: ContentResolver get() = context.contentResolver

    override fun hasMediaPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private val refreshLock = Any()
    private val _onThisDayIndex = MutableStateFlow<OnThisDayIndex?>(null)
    override val onThisDayIndex: StateFlow<OnThisDayIndex?> = _onThisDayIndex.asStateFlow()

    // A refresh that starts while another one runs waits for it instead of querying again
    private var refreshInFlight: CompletableDeferred<Unit>? = null

    override suspend fun refreshOnThisDayIndex() {
        // Without the permission MediaStore returns an empty cursor, which must not look like an empty gallery
        if (!hasMediaPermission()) throw PermissionDeniedException("Media read permission is not granted")
        var owner = false
        val run = synchronized(refreshLock) {
            refreshInFlight ?: CompletableDeferred<Unit>().also {
                refreshInFlight = it
                owner = true
            }
        }
        if (!owner) {
            run.await()
            return
        }
        try {
            _onThisDayIndex.value = withContext(ioDispatcher) { readOnThisDayIndex() }
            run.complete(Unit)
        } catch (e: Throwable) {
            run.completeExceptionally(e)
            throw e
        } finally {
            synchronized(refreshLock) { refreshInFlight = null }
        }
    }

    /** One query over every image of the gallery: no filter on type, folder or date. */
    private fun readOnThisDayIndex(): OnThisDayIndex {
        val diagnostics = PerfLog.isEnabled()
        val startedAt = PerfLog.nanoTime()
        val zone = ZoneId.systemDefault()
        val currentYear = LocalDate.now(clock).year
        val builder = OnThisDayIndexBuilder(currentYear, PhotosConstants.MAX_YEARS_BACK, zone)
        val stats = IndexLoadStats()

        val projection = buildList {
            addAll(INDEX_PROJECTION)
            if (diagnostics) {
                add(MediaStore.Images.Media.MIME_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()
        val cursor = resolver.query(COLLECTION_URI, projection, null, null, null)
        val queryMs = PerfLog.sinceMs(startedAt)
        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val mimeColumn = it.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                -1
            }
            while (it.moveToNext()) {
                stats.rows++
                val taken = if (it.isNull(takenColumn)) null else it.getLong(takenColumn)
                val added = if (it.isNull(addedColumn)) null else it.getLong(addedColumn)
                val takenMissing = taken == null || taken <= 0
                if (takenMissing) stats.dateTakenMissing++
                val millis = effectiveTakenAtMillis(taken, added)
                if (millis == null) {
                    stats.unusable++
                } else {
                    if (takenMissing) stats.usedDateAdded++
                    builder.add(IndexedImage(it.getLong(idColumn), millis))
                }
                if (diagnostics) {
                    if (mimeColumn >= 0) stats.byMime.merge(it.getString(mimeColumn) ?: "null", 1, Int::plus)
                    if (pathColumn >= 0) stats.byPath.merge(it.getString(pathColumn) ?: "null", 1, Int::plus)
                }
            }
        }

        val index = OnThisDayIndex(
            currentYear = currentYear,
            entries = builder.entries().mapValues { (_, value) ->
                val (count, cover) = value
                DayMemories(
                    count = count,
                    cover = DayCover(cover.id, ContentUris.withAppendedId(COLLECTION_URI, cover.id), cover.takenOn),
                )
            },
        )
        PerfLog.d {
            "index: query=${queryMs}ms total=${PerfLog.sinceMs(startedAt)}ms rows=${stats.rows} " +
                "dateTakenNullOrZero=${stats.dateTakenMissing} usedDateAdded=${stats.usedDateAdded} " +
                "unusable=${stats.unusable} inWindow=${builder.counted} entries=${index.entries.size} " +
                "years=${currentYear - PhotosConstants.MAX_YEARS_BACK}..${currentYear - 1}"
        }
        PerfLog.d { "index: by mime type ${stats.byMime.toSortedMap()}" }
        PerfLog.d { "index: by relative path ${stats.byPath.toSortedMap()}" }
        PerfLog.d { "index: permissions ${permissionStates()}" }
        return index
    }

    /** The raw state of every photo permission that applies to this Android version, to tell full from partial access. */
    private fun permissionStates(): String {
        fun state(permission: String) =
            if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "granted" else "denied"
        return buildString {
            append("sdk=${Build.VERSION.SDK_INT}")
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    append(" READ_MEDIA_IMAGES=${state(Manifest.permission.READ_MEDIA_IMAGES)}")
                    append(" READ_MEDIA_VISUAL_USER_SELECTED=${state(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)}")
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    append(" READ_MEDIA_IMAGES=${state(Manifest.permission.READ_MEDIA_IMAGES)}")
                else -> append(" READ_EXTERNAL_STORAGE=${state(Manifest.permission.READ_EXTERNAL_STORAGE)}")
            }
        }
    }

    private class IndexLoadStats {
        var rows = 0
        var dateTakenMissing = 0
        var usedDateAdded = 0
        var unusable = 0
        val byMime = HashMap<String, Int>()
        val byPath = HashMap<String, Int>()
    }

    override suspend fun getPhotosForDate(date: LocalDate): List<Photo> = withContext(ioDispatcher) {
        val zone = ZoneId.systemDefault()
        val startedAt = PerfLog.nanoTime()
        val cursor = queryOnThisDay(date, zone)
        val queryMs = PerfLog.sinceMs(startedAt)
        val photos = cursor?.use {
            buildList {
                while (it.moveToNext()) add(it.toPhoto(zone))
            }
        }.orEmpty()
        PerfLog.d {
            "mediastore getPhotosForDate date=$date query=${queryMs}ms total=${PerfLog.sinceMs(startedAt)}ms rows=${photos.size}"
        }
        photos
    }

    override suspend fun getPhotoLocation(photoId: Long): String? = withContext(ioDispatcher) {
        try {
            val (latitude, longitude) = coordinatesOf(ContentUris.withAppendedId(COLLECTION_URI, photoId))
                ?: return@withContext null
            geocoder.getLocation(latitude, longitude)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A location is a nice-to-have, so any failure just means there is none
            Log.e(TAG, "Could not look up the location of photo $photoId", e)
            null
        }
    }

    /**
     * On API 30+ the user must confirm via the returned [DeleteResult.RequiresConfirmation].
     * Below 30 photos are deleted directly; a [SecurityException] is thrown for photos
     * the app does not own.
     */
    override suspend fun deletePhotos(photoIds: List<Long>): DeleteResult =
        withContext(ioDispatcher) {
            if (photoIds.isEmpty()) return@withContext DeleteResult.Deleted
            val uris = photoIds.map { ContentUris.withAppendedId(COLLECTION_URI, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val request = MediaStore.createDeleteRequest(resolver, uris)
                DeleteResult.RequiresConfirmation(request.intentSender)
            } else {
                uris.forEach { resolver.delete(it, null, null) }
                DeleteResult.Deleted
            }
        }

    /**
     * API 29+ redacts GPS data in MediaStore, so it is read from the original file's EXIF (needs
     * ACCESS_MEDIA_LOCATION). Below that the deprecated MediaStore columns still hold it.
     */
    private fun coordinatesOf(uri: Uri): Pair<Double, Double>? {
        val coordinates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) readExifCoordinates(uri) else null
        } else {
            queryLegacyCoordinates(uri)
        }
        // 0/0 is how missing GPS data is commonly stored
        return coordinates?.takeUnless {
            it.first == LocationGeocoder.INVALID_COORDINATE && it.second == LocationGeocoder.INVALID_COORDINATE
        }
    }

    private fun queryLegacyCoordinates(uri: Uri): Pair<Double, Double>? =
        resolver.query(uri, arrayOf(LATITUDE_COLUMN, LONGITUDE_COLUMN), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0) && !it.isNull(1)) it.getDouble(0) to it.getDouble(1) else null
        }

    /** Reads the GPS position from the original file's EXIF. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readExifCoordinates(uri: Uri): Pair<Double, Double>? = try {
        resolver.openInputStream(MediaStore.setRequireOriginal(uri))?.use { stream ->
            ExifInterface(stream).latLong?.let { it[0] to it[1] }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not read the EXIF coordinates", e)
        null
    }

    /**
     * A cursor over the photos taken on the same month/day as [date] in earlier years, oldest first,
     * or null when there is no such year. The time zone is read per query so a changed device zone is
     * picked up; it only sets the day boundaries, the caller decides which date "today" is.
     */
    private fun queryOnThisDay(date: LocalDate, zone: ZoneId): Cursor? {
        // Without the permission MediaStore returns an empty cursor, which must not look like an empty day
        if (!hasMediaPermission()) throw PermissionDeniedException("Media read permission is not granted")
        val monthDay = MonthDay.from(date)
        val ranges = (date.year - 1 downTo date.year - PhotosConstants.MAX_YEARS_BACK)
            // A Feb 29 photo is only looked up in leap years, by design: other years are skipped,
            // not mapped to Feb 28 or Mar 1
            .filter { monthDay.isValidYear(it) }
            .map { year ->
                val day = monthDay.atYear(year)
                day.atStartOfDay(zone).toInstant().toEpochMilli() to
                    day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            }
        if (ranges.isEmpty()) return null

        val selection = ranges.joinToString(" OR ") {
            "(${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?)"
        }
        val args = ranges.flatMap { listOf(it.first.toString(), it.second.toString()) }.toTypedArray()
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} ASC"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
            }
            resolver.query(COLLECTION_URI, PROJECTION, queryArgs, null)
        } else {
            resolver.query(COLLECTION_URI, PROJECTION, selection, args, sort)
        }
    }

    private fun Cursor.toPhoto(zone: ZoneId): Photo {
        val id = getLong(getColumnIndexOrThrow(MediaStore.Images.Media._ID))
        return Photo(
            id = id,
            uri = ContentUris.withAppendedId(COLLECTION_URI, id),
            dateTaken = Instant.ofEpochMilli(getLong(getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)))
                .atZone(zone)
                .toLocalDate(),
            sizeBytes = getLong(getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)),
        )
    }


    private companion object {
        const val TAG = "PhotoRepositoryImpl"

        val INDEX_PROJECTION = listOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val COLLECTION_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        // The LATITUDE/LONGITUDE columns are deprecated and empty since API 29, so they are
        // only read below it, for a single photo (see readExifCoordinates for API 29+).
        @Suppress("DEPRECATION")
        const val LATITUDE_COLUMN = MediaStore.Images.ImageColumns.LATITUDE

        @Suppress("DEPRECATION")
        const val LONGITUDE_COLUMN = MediaStore.Images.ImageColumns.LONGITUDE

        val PROJECTION: Array<String> = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
        )
    }
}
