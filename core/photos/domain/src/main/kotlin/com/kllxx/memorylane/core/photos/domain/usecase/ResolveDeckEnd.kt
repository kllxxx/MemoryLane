package com.kllxx.memorylane.core.photos.domain.usecase

import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import javax.inject.Inject

/** Where the user goes once every photo of a deck has a decision. */
enum class DeckEnd {
    /** Some photo is marked for deletion, in this deck or in an earlier one: «Перед удалением». */
    BeforeDelete,

    /** Nothing to delete: «Итоги». */
    Result,
}

/**
 * The end of a deck: a day is completed (the undated deck is not a day and has no session), and the marks left in any
 * deck decide the next screen. The user has finished swiping, so a failure to save the session or to read the stored
 * marks is logged and never keeps them on the screen.
 */
class ResolveDeckEnd @Inject constructor(
    private val completeDay: CompleteDay,
    private val swipeRepository: SwipeRepository,
) {

    /** [hasMarksInDeck]: the deck that was just swiped already holds a photo marked for deletion. */
    suspend operator fun invoke(deck: DeckKey, hasMarksInDeck: Boolean): DeckEnd {
        if (deck is DeckKey.Day) {
            bestEffort("save the session of ${deck.date}", Unit) { completeDay(deck.date) }
        }
        // The marks of this deck are known already; the stored ones add those of the days before
        val hasMarks = hasMarksInDeck || bestEffort("read the pending deletions", false) {
            swipeRepository.getPendingDeletes().isNotEmpty()
        }
        return if (hasMarks) DeckEnd.BeforeDelete else DeckEnd.Result
    }
}
