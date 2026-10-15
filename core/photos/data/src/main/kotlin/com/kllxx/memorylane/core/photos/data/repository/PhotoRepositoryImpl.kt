package com.kllxx.memorylane.core.photos.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.kllxx.memorylane.core.photos.data.geocoder.LocationGeocoder
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import javax.inject.Inject

internal class PhotoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geocoder: LocationGeocoder,
) : PhotoRepository {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun getPhotosForDate(date: LocalDate, includeLocation: Boolean): List<Photo> =
        withContext(Dispatchers.IO) {
            queryOnThisDay(date, limit = null).map { resolveLocation(it, includeLocation) }
        }

    override suspend fun getFirstPhotoForDate(date: LocalDate, includeLocation: Boolean): Photo? =
        withContext(Dispatchers.IO) {
            queryOnThisDay(date, limit = 1).firstOrNull()?.let { resolveLocation(it, includeLocation) }
        }

    /**
     * On API 30+ the user must confirm via the returned [DeleteResult.RequiresConfirmation].
     * Below 30 photos are deleted directly; a [SecurityException] is thrown for photos
     * the app does not own.
     */
    override suspend fun deletePhotos(photoIds: List<Long>): DeleteResult =
        withContext(Dispatchers.IO) {
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

    /** A photo plus the coordinates the MediaStore columns provide (API 24-28 only). */
    private class QueriedPhoto(val photo: Photo, val latitude: Double?, val longitude: Double?)

    private suspend fun resolveLocation(queried: QueriedPhoto, includeLocation: Boolean): Photo {
        if (!includeLocation) return queried.photo
        val (latitude, longitude) = coordinatesOf(queried) ?: return queried.photo
        return queried.photo.copy(location = geocoder.getLocation(latitude, longitude))
    }

    private fun coordinatesOf(queried: QueriedPhoto): Pair<Double, Double>? {
        val coordinates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) readExifCoordinates(queried.photo.uri) else null
        } else if (queried.latitude != null && queried.longitude != null) {
            queried.latitude to queried.longitude
        } else {
            null
        }
        // 0/0 is how missing GPS data is commonly stored
        return coordinates?.takeUnless { it.first == 0.0 && it.second == 0.0 }
    }

    /** Since API 29 MediaStore redacts GPS data, so it is read from the original file's EXIF. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readExifCoordinates(uri: Uri): Pair<Double, Double>? = try {
        resolver.openInputStream(MediaStore.setRequireOriginal(uri))?.use { stream ->
            val latLong = FloatArray(2)
            if (ExifInterface(stream).getLatLong(latLong)) latLong[0].toDouble() to latLong[1].toDouble() else null
        }
    } catch (e: Exception) {
        null
    }

    private fun queryOnThisDay(date: LocalDate, limit: Int?): List<QueriedPhoto> {
        val zone = ZoneId.systemDefault()
        val monthDay = MonthDay.from(date)
        val ranges = (date.year - 1 downTo date.year - MAX_YEARS_BACK)
            .filter { monthDay.isValidYear(it) }
            .map { year ->
                val day = monthDay.atYear(year)
                day.atStartOfDay(zone).toInstant().toEpochMilli() to
                    day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            }
        if (ranges.isEmpty()) return emptyList()

        val selection = ranges.joinToString(" OR ") {
            "(${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?)"
        }
        val args = ranges.flatMap { listOf(it.first.toString(), it.second.toString()) }.toTypedArray()
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} ASC"

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
                if (limit != null) putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            }
            resolver.query(COLLECTION_URI, PROJECTION, queryArgs, null)
        } else {
            val sortWithLimit = if (limit != null) "$sort LIMIT $limit" else sort
            resolver.query(COLLECTION_URI, PROJECTION, selection, args, sortWithLimit)
        }

        return cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val latCol = it.getColumnIndex(LATITUDE_COLUMN)
            val lonCol = it.getColumnIndex(LONGITUDE_COLUMN)
            buildList {
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val photo = Photo(
                        id = id,
                        uri = ContentUris.withAppendedId(COLLECTION_URI, id),
                        dateTaken = Instant.ofEpochMilli(it.getLong(takenCol))
                            .atZone(zone)
                            .toLocalDate(),
                        sizeBytes = it.getLong(sizeCol),
                    )
                    val hasCoordinates = latCol >= 0 && lonCol >= 0 && !it.isNull(latCol) && !it.isNull(lonCol)
                    add(
                        QueriedPhoto(
                            photo = photo,
                            latitude = if (hasCoordinates) it.getDouble(latCol) else null,
                            longitude = if (hasCoordinates) it.getDouble(lonCol) else null,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private companion object {
        const val MAX_YEARS_BACK = 30
        val COLLECTION_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        // The LATITUDE/LONGITUDE columns are deprecated and empty since API 29, so they are
        // only queried below it (see readExifCoordinates for API 29+).
        @Suppress("DEPRECATION")
        const val LATITUDE_COLUMN = MediaStore.Images.ImageColumns.LATITUDE

        @Suppress("DEPRECATION")
        const val LONGITUDE_COLUMN = MediaStore.Images.ImageColumns.LONGITUDE

        val PROJECTION: Array<String> = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.SIZE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(LATITUDE_COLUMN)
                add(LONGITUDE_COLUMN)
            }
        }.toTypedArray()
    }
}
