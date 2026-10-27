package com.kllxx.memorylane.core.photos.domain.repository

import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.photos.domain.model.Photo
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

interface PhotoRepository {
    /** Whether the permission to read the photo library is currently granted. */
    fun hasMediaPermission(): Boolean

    /**
     * The last loaded index of the photos taken on each month/day in earlier years, or null until the first
     * [refreshOnThisDayIndex] has finished. The value is kept, so a screen that comes back sees it immediately.
     */
    val onThisDayIndex: StateFlow<OnThisDayIndex?>

    /**
     * Reads the whole gallery with one query and publishes the new [onThisDayIndex]. A refresh that is
     * called while another one is running waits for it instead of querying again.
     *
     * @throws com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
     * if the media permission is not granted.
     */
    suspend fun refreshOnThisDayIndex()

    /**
     * Photos taken on the same month/day as [date] in earlier years, oldest first.
     * [Photo.location] is always null here; geocoding is slow, so use [getPhotoLocation] per photo.
     *
     * @throws com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
     * if the media permission is not granted.
     */
    suspend fun getPhotosForDate(date: LocalDate): List<Photo>

    /** "City, Street" (or just "City") for one photo, or null when it has no usable location. Never throws. */
    suspend fun getPhotoLocation(photoId: Long): String?

    suspend fun deletePhotos(photoIds: List<Long>): DeleteResult
}
