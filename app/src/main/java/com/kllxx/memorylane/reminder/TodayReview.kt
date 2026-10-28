package com.kllxx.memorylane.reminder

import android.util.Log
import com.kllxx.memorylane.core.domain.model.DayCompletion
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Whether today has something to review, which is what the reminder is for and where its tap goes: photos from earlier
 * years that are not completed yet. The same rule as the banner of today in Calendar.
 */
class TodayReview @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val swipeRepository: SwipeRepository,
    private val clock: Clock,
) {

    /** How many photos of today are waiting, or 0 when there is nothing to review: no access, no photos, or all decided. */
    suspend fun photosToReview(): Int {
        if (!photoRepository.hasMediaPermission()) return 0
        val today = LocalDate.now(clock)
        return try {
            val photos = photoRepository.getPhotosForDate(today)
            val decisions = swipeRepository.getDecisionsByDate(today)
            if (DayCompletion.isCompleted(photos.map { it.id }, decisions)) 0 else photos.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not knowing is not a reason to send someone to a screen: Calendar is where they land
            Log.e(TAG, "Could not look up the photos of today", e)
            0
        }
    }

    private companion object {
        const val TAG = "TodayReview"
    }
}
