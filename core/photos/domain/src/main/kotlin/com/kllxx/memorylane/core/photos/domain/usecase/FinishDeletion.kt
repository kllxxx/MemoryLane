package com.kllxx.memorylane.core.photos.domain.usecase

import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.model.DeletionOutcome
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import javax.inject.Inject

/**
 * The numbers «Итоги» shows. The deleted photos and the freed space cover every deck that was deleted from; the kept
 * and total counts are those of the deck the user came from.
 */
data class DeckResult(
    val deletedCount: Int,
    val freedBytes: Long,
    val keptCount: Int,
    val totalCount: Int,
)

/**
 * The end of «Перед удалением». Everything here is best effort: the deletion is recorded already, or nothing was
 * deleted, so a failure is logged and the user still gets their result.
 */
class FinishDeletion @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val swipeRepository: SwipeRepository,
    private val completeDay: CompleteDay,
) {

    /** A deletion was recorded: the photo index is refreshed, so the deleted photos are gone from the Calendar. */
    suspend fun afterDeletion(deck: DeckKey, outcome: DeletionOutcome): DeckResult {
        bestEffort("refresh the photo index after the deletion", Unit) { photoRepository.refreshOnThisDayIndex() }
        return resultOf(deck, outcome)
    }

    /** Nothing is marked any more: the day (the undated deck is not one) is completed without deleting anything. */
    suspend fun withoutDeleting(deck: DeckKey): DeckResult {
        if (deck is DeckKey.Day) {
            bestEffort("save the session of ${deck.date}", Unit) { completeDay(deck.date) }
        }
        return resultOf(deck, DeletionOutcome(deletedCount = 0, freedBytes = 0))
    }

    private suspend fun resultOf(deck: DeckKey, outcome: DeletionOutcome): DeckResult {
        val decisions = bestEffort("read the decisions of $deck", emptyMap()) { swipeRepository.getDecisions(deck) }
        return DeckResult(
            deletedCount = outcome.deletedCount,
            freedBytes = outcome.freedBytes,
            keptCount = decisions.count { it.value == PhotoDecision.KEEP },
            totalCount = decisions.size,
        )
    }
}
