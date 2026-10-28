package com.kllxx.memorylane.feature.swipe.impl.swipe

import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.photos.domain.model.Photo
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One photo of the deck. The place of the photo, once found, is [Photo.location]. */
data class DeckPhoto(
    val photo: Photo,
    /** Null while the photo is undecided. */
    val decision: PhotoDecision?,
    /** When the photo was taken, as shown on the card, e.g. «7 января 2016, 21:15». */
    val takenAtText: String,
    /**
     * The width over the height of the photo as it is shown (orientation applied), or null when MediaStore does not
     * know its size. The card takes this shape, so the photo is never cropped.
     */
    val aspectRatio: Float? = null,
) {
    val id: Long get() = photo.id
}

/** What one swipe changed, so that undo can put it back: the decision the photo had before ([previous], null if none). */
data class UndoEntry(val photoId: Long, val previous: PhotoDecision?)

/**
 * [date] comes from the route argument, so it has no default.
 *
 * [deck] holds the photos that still exist, oldest first; [currentIndex] points into it and equals the deck size
 * once the last photo is done ([isFinished]). [trashed] holds the photos of the day that are in the system trash,
 * oldest first: they appear in the strip only, are view-only and can never be swiped.
 */
data class SwipeState(
    val date: LocalDate,
    val deck: ImmutableList<DeckPhoto> = persistentListOf(),
    val trashed: ImmutableList<DeckPhoto> = persistentListOf(),
    val currentIndex: Int = 0,
    /** The decisions of this visit, newest last. Not kept when the screen is left. */
    val undoStack: ImmutableList<UndoEntry> = persistentListOf(),
    val buttonsMode: Boolean = false,
    val isLoading: Boolean = false,
    val isFinished: Boolean = false,
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    val currentPhoto: DeckPhoto? get() = deck.getOrNull(currentIndex)

    /** Every photo of the day that still exists has a decision. */
    val isDayCompleted: Boolean get() = deck.isNotEmpty() && deck.all { it.decision != null }
}
