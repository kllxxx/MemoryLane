package com.kllxx.memorylane.core.photos.domain.repository

import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.model.PhotoLocation
import com.kllxx.memorylane.core.domain.model.DeckKey
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
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
     * Photos taken on the same month/day as [date] in earlier years, oldest first (by [Photo.takenAt]).
     * [Photo.location] is always null here; geocoding is slow, so use [resolveLocations].
     *
     * @throws com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
     * if the media permission is not granted.
     */
    suspend fun getPhotosForDate(date: LocalDate): List<Photo>

    /**
     * The photos that have no date anywhere (see [OnThisDayIndex.undatedIds]), in the order of the undated deck: by
     * the moment they were added to the library, then by id. That moment is only for the order and is never shown.
     * [Photo.takenAt] is null and [Photo.location] is always null here; use [resolveLocations].
     *
     * @throws com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
     * if the media permission is not granted.
     */
    suspend fun getUndatedPhotos(): List<Photo>

    /**
     * The day each of [photoIds] is on now, for the photos that are in the library and have a date: the `DATE_TAKEN`
     * of MediaStore, or the date the date chain found in the file. A photo without any date, or that is gone, is
     * missing from the result.
     *
     * @throws com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
     * if the media permission is not granted.
     */
    suspend fun getPhotoDates(photoIds: Collection<Long>): Map<Long, LocalDate>

    /**
     * The photos among [ids] that are in the system trash (API 30+), oldest first. Empty below API 30, where
     * there is no trash. An id whose photo has been purged from the trash is simply missing.
     */
    suspend fun getTrashedPhotos(ids: Collection<Long>): List<Photo>

    /**
     * The "City, Country" of each of [photos], one result at a time and in the given order, so the caller decides
     * which photo comes first. Places already known are emitted at once. A photo without GPS data or without a
     * place is emitted with a null location, and a lookup that failed is left out so it is tried again next time.
     * Never throws, apart from cancellation.
     */
    fun resolveLocations(photos: List<Photo>): Flow<PhotoLocation>

    /**
     * The size in bytes of each of [photoIds] that is in the library, read now. Photos that are gone are missing.
     * Read this before a deletion is requested: afterwards the trashed photos are no longer in a normal query.
     *
     * @throws com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
     * if the media permission is not granted.
     */
    suspend fun getPhotoSizes(photoIds: Collection<Long>): Map<Long, Long>

    /**
     * Starts the deletion of [photoIds]. On API 30+ they go to the system trash after the user confirms the returned
     * [DeleteResult.RequiresConfirmation]. Below 30 there is no trash: they are deleted for good, at once.
     */
    suspend fun deletePhotos(photoIds: List<Long>): DeleteResult
}

/** The photos of [deck]: a day's On This Day photos, or the undated ones. */
suspend fun PhotoRepository.getPhotos(deck: DeckKey): List<Photo> = when (deck) {
    is DeckKey.Day -> getPhotosForDate(deck.date)
    DeckKey.Undated -> getUndatedPhotos()
}
