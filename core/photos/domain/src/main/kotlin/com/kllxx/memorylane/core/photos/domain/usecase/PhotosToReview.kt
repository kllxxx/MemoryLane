package com.kllxx.memorylane.core.photos.domain.usecase

import com.kllxx.memorylane.core.domain.model.DayCompletion
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * The one answer to "is this day completed, and how many photos are waiting": the banner of today in Calendar and the
 * reminder both ask it, so they cannot disagree.
 *
 * A day is completed when every photo that still exists has a decision, a mark for deletion included
 * ([DayCompletion]). [invoke] is the number of existing photos of [date] while the day is not completed, and 0 when
 * there is nothing to review: no access to the photos, no photos left, or every one decided. It asks the gallery and
 * the decisions each time and does not look at the photo index, which can be stale. It only reads: moving the decisions
 * of photos that got a date to their day is [AdoptDatedDecisions], which a caller that needs it calls first. A failure
 * to read either is thrown to the caller, which decides what not knowing means for it.
 */
class PhotosToReview @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val swipeRepository: SwipeRepository,
) {

    suspend operator fun invoke(date: LocalDate): Int {
        if (!photoRepository.hasMediaPermission()) return 0
        val photos = photoRepository.getPhotosForDate(date)
        val decisions = swipeRepository.getDecisions(DeckKey.Day(date))
        return if (DayCompletion.isCompleted(photos.map { it.id }, decisions)) 0 else photos.size
    }
}
