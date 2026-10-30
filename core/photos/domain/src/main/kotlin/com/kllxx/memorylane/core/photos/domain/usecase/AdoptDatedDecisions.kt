package com.kllxx.memorylane.core.photos.domain.usecase

import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Takes the decisions of photos that were undated and have a date now to the day they are on, so that a pending mark
 * for deletion shows up in «Перед удалением» under its day at once, without that day being opened.
 *
 * The photos to look at are those with a decision in the undated deck that [undatedIds] (the undated photos of the last
 * photo index) no longer holds. Of those, only the photos that have a date are moved: one that is gone, or whose file
 * has not been read yet, keeps its decision where it is. A failure to read either side is thrown to the caller.
 */
class AdoptDatedDecisions @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val swipeRepository: SwipeRepository,
) {

    /** How many photos were moved to their day. */
    suspend operator fun invoke(undatedIds: Set<Long>): Int {
        if (!photoRepository.hasMediaPermission()) return 0
        val candidates = swipeRepository.getDecisions(DeckKey.Undated).keys.filter { it !in undatedIds }
        if (candidates.isEmpty()) return 0
        val dates = photoRepository.getPhotoDates(candidates)
        dates.entries.groupBy({ it.value }, { it.key }).forEach { (date, ids) ->
            swipeRepository.adoptUndatedDecisions(ids, date)
        }
        return dates.size
    }

    /**
     * The same for the photos of one [date] that are already known (the deck of the day was just read): their decisions
     * from the undated deck move to [date]. No photos, nothing to move.
     */
    suspend fun forDay(date: LocalDate, photoIds: Collection<Long>) {
        if (photoIds.isEmpty()) return
        swipeRepository.adoptUndatedDecisions(photoIds, date)
    }
}
