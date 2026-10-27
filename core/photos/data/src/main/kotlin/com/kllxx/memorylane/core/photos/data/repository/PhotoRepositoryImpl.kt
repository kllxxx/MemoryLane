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
import com.kllxx.memorylane.core.photos.data.PhotosConstants
import com.kllxx.memorylane.core.photos.data.di.IoDispatcher
import com.kllxx.memorylane.core.photos.data.geocoder.LocationGeocoder
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import javax.inject.Inject

internal class PhotoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geocoder: LocationGeocoder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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

    override suspend fun getPhotosForDate(date: LocalDate): List<Photo> = withContext(ioDispatcher) {
        val zone = ZoneId.systemDefault()
        val startedAt = System.nanoTime()
        val cursor = queryOnThisDay(date, zone)
        val queryMs = elapsedMs(startedAt)
        val photos = cursor?.use {
            buildList {
                while (it.moveToNext()) add(it.toPhoto(zone))
            }
        }.orEmpty()
        logQuery("getPhotosForDate", date, queryMs, startedAt, photos.size)
        photos
    }

    override suspend fun getDayInfo(date: LocalDate): Pair<Photo?, Int> = withContext(ioDispatcher) {
        val zone = ZoneId.systemDefault()
        val startedAt = System.nanoTime()
        val cursor = queryOnThisDay(date, zone)
        val queryMs = elapsedMs(startedAt)
        val info = cursor?.use {
            // Only the first row becomes a Photo; the rest are just counted
            val first = if (it.moveToFirst()) it.toPhoto(zone) else null
            first to it.count
        } ?: (null to 0)
        logQuery("getDayInfo", date, queryMs, startedAt, info.second)
        info
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

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / NANOS_PER_MILLI

    /** One line per MediaStore query: what was asked, how long the query and the whole call took, and the rows found. */
    private fun logQuery(what: String, date: LocalDate, queryMs: Long, startedAt: Long, rows: Int) {
        Timber.tag(PERF_TAG).d(
            "[${Thread.currentThread().name}] mediastore $what date=$date " +
                "(on this day, up to ${PhotosConstants.MAX_YEARS_BACK} years back) " +
                "query=${queryMs}ms total=${elapsedMs(startedAt)}ms rows=$rows",
        )
    }

    private companion object {
        const val TAG = "PhotoRepositoryImpl"
        const val PERF_TAG = "CalendarPerf"
        const val NANOS_PER_MILLI = 1_000_000L
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
