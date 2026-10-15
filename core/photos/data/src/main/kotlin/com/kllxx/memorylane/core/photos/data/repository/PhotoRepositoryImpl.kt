package com.kllxx.memorylane.core.photos.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
) : PhotoRepository {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun getPhotosForDate(date: LocalDate): List<Photo> =
        withContext(Dispatchers.IO) { queryOnThisDay(date, limit = null) }

    override suspend fun getFirstPhotoForDate(date: LocalDate): Photo? =
        withContext(Dispatchers.IO) { queryOnThisDay(date, limit = 1).firstOrNull() }

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

    private fun queryOnThisDay(date: LocalDate, limit: Int?): List<Photo> {
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
            buildList {
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    add(
                        Photo(
                            id = id,
                            uri = ContentUris.withAppendedId(COLLECTION_URI, id),
                            dateTaken = Instant.ofEpochMilli(it.getLong(takenCol))
                                .atZone(zone)
                                .toLocalDate(),
                            sizeBytes = it.getLong(sizeCol),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private companion object {
        const val MAX_YEARS_BACK = 30
        val COLLECTION_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
        )
    }
}
