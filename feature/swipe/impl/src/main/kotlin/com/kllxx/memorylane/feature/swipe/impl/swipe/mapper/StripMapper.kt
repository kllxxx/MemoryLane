package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import android.net.Uri
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.feature.swipe.impl.swipe.DeckPhoto

/**
 * One thumbnail of the strip. A photo of the deck has its [deckIndex]; a photo in the system trash has none: it is
 * view-only and can never become the current card.
 */
data class StripItem(
    val photoId: Long,
    val uri: Uri,
    val decision: PhotoDecision?,
    val deckIndex: Int?,
) {
    val isTrashed: Boolean get() = deckIndex == null
}

/** What the dot under a thumbnail shows. */
enum class StripDot { None, Keep, Delete }

private const val UNDECIDED_ALPHA = 0.55f
private const val TRASHED_ALPHA = 0.4f

/**
 * The deck and the trashed photos in one list, oldest first by capture time (the id breaks a tie). The photos of the
 * undated deck have no capture time: they keep the order of the deck, with the trashed ones after them.
 */
fun buildStrip(deck: List<DeckPhoto>, trashed: List<DeckPhoto>): List<StripItem> {
    val inDeck = deck.mapIndexed { index, item -> item to index }
    val inTrash = trashed.map { it to null }
    return (inDeck + inTrash)
        .sortedWith(
            if (deck.any { it.photo.takenAt == null }) {
                compareBy<Pair<DeckPhoto, Int?>> { it.second == null }.thenBy { it.second }.thenBy { it.first.id }
            } else {
                compareBy<Pair<DeckPhoto, Int?>> { it.first.photo.takenAt }.thenBy { it.first.id }
            },
        )
        .map { (item, deckIndex) ->
            StripItem(
                photoId = item.id,
                uri = item.photo.uri,
                decision = if (deckIndex == null) PhotoDecision.TRASHED else item.decision,
                deckIndex = deckIndex,
            )
        }
}

/** Where the photo at [deckIndex] of the deck is in [strip], or -1. */
fun stripPositionOf(strip: List<StripItem>, deckIndex: Int): Int = strip.indexOfFirst { it.deckIndex == deckIndex }

fun StripItem.dot(): StripDot = when (decision) {
    PhotoDecision.KEEP -> StripDot.Keep
    PhotoDecision.DELETE, PhotoDecision.TRASHED, PhotoDecision.DELETED -> StripDot.Delete
    null -> StripDot.None
}

/** Trashed photos are faded most, undecided ones a little. */
fun StripItem.alpha(): Float = when {
    isTrashed -> TRASHED_ALPHA
    decision == null -> UNDECIDED_ALPHA
    else -> 1f
}
