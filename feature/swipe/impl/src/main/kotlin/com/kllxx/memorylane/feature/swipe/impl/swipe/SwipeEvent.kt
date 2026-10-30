package com.kllxx.memorylane.feature.swipe.impl.swipe

/** Results produced by [SwipeViewModel] itself. The UI never sends these. */
sealed interface SwipeEvent {
    data class PhotosLoaded(
        /** The photos that exist, oldest first, each with the decision it already has. */
        val deck: List<DeckPhoto>,
        /** The photos of the day that are in the system trash, oldest first. */
        val trashed: List<DeckPhoto> = emptyList(),
        /** The card index saved before the process died; null on a fresh visit, which starts at the first undecided photo. */
        val restoredIndex: Int? = null,
        /** The undo stack saved before the process died, oldest first. */
        val restoredUndo: List<UndoEntry> = emptyList(),
    ) : SwipeEvent

    /** The place of one photo. Null if it has none, which changes nothing. */
    data class LocationLoaded(val photoId: Long, val location: String?) : SwipeEvent

    data class ButtonsModeChanged(val enabled: Boolean) : SwipeEvent

    /** The decisions of the day as stored now, by photo id. */
    data class DecisionsReloaded(val decisions: Map<Long, com.kllxx.memorylane.core.domain.model.PhotoDecision>) : SwipeEvent
}
