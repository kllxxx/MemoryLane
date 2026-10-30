package com.kllxx.memorylane.reminder

import android.util.Log
import com.kllxx.memorylane.core.domain.time.TodayProvider
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.core.photos.domain.usecase.AdoptDatedDecisions
import com.kllxx.memorylane.core.photos.domain.usecase.PhotosToReview
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Whether today has something to review, which is what the reminder is for and where its tap goes: photos from earlier
 * years that are not completed yet. The rule is [PhotosToReview], the same one the banner of today in Calendar uses.
 */
class TodayReview @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val adoptDatedDecisions: AdoptDatedDecisions,
    private val review: PhotosToReview,
    private val todayProvider: TodayProvider,
) {

    /** How many photos of today are waiting, or 0 when there is nothing to review or it cannot be told. */
    suspend fun photosToReview(): Int = photosToReview(todayProvider.now())

    /** The same for [date]: a caller that acts on the answer asks for the date it will act on. */
    suspend fun photosToReview(date: LocalDate): Int = try {
        if (photoRepository.hasMediaPermission()) {
            // A photo decided as undated that has a date now is decided on its day; counting is only reading
            adoptDatedDecisions.forDay(date, photoRepository.getPhotosForDate(date).map { it.id })
        }
        review(date)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Not knowing is not a reason to send someone to a screen: Calendar is where they land
        Log.e(TAG, "Could not look up the photos of today", e)
        0
    }

    private companion object {
        const val TAG = "TodayReview"
    }
}
