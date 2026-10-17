package com.kllxx.memorylane.core.photos.domain.repository

import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.model.Photo
import java.time.LocalDate

interface PhotoRepository {
    /** Whether the permission to read the photo library is currently granted. */
    fun hasMediaPermission(): Boolean

    /**
     * Photos taken on the same month/day as [date] in earlier years, oldest first.
     * Geocoding is slow, so [Photo.location] is only resolved when [includeLocation] is true.
     *
     * @throws com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
     * if the media permission is not granted.
     */
    suspend fun getPhotosForDate(date: LocalDate, includeLocation: Boolean = false): List<Photo>

    /** The chronologically first photo from [getPhotosForDate], or null if there is none. */
    suspend fun getFirstPhotoForDate(date: LocalDate, includeLocation: Boolean = false): Photo?

    suspend fun deletePhotos(photoIds: List<Long>): DeleteResult
}
