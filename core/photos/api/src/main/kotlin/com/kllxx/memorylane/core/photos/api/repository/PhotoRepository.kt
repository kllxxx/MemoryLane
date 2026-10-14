package com.kllxx.memorylane.core.photos.api.repository

import com.kllxx.memorylane.core.photos.api.model.DeleteResult
import com.kllxx.memorylane.core.photos.api.model.Photo
import java.time.LocalDate

interface PhotoRepository {
    /** Photos taken on the same month/day as [date] in earlier years, oldest first. */
    suspend fun getPhotosForDate(date: LocalDate): List<Photo>

    /** The chronologically first photo from [getPhotosForDate], or null if there is none. */
    suspend fun getFirstPhotoForDate(date: LocalDate): Photo?

    suspend fun deletePhotos(photoIds: List<Long>): DeleteResult
}
