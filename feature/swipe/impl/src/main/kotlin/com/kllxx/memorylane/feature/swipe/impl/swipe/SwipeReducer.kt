package com.kllxx.memorylane.feature.swipe.impl.swipe

import com.haroncode.lazycardstack.swiper.SwipeDirection
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

object SwipeReducer {

    fun reduce(state: SwipeState, intent: SwipeIntent): SwipeState = when (intent) {
        is SwipeIntent.Swipe -> swipe(state, intent.direction)
        SwipeIntent.Undo -> undo(state)
        is SwipeIntent.JumpTo -> jumpTo(state, intent.index)
        // Handled by the ViewModel: the setting is stored first, the screen follows the stored value
        SwipeIntent.ToggleButtonsMode,
        SwipeIntent.Share,
        SwipeIntent.OpenInGallery,
        SwipeIntent.Close,
        SwipeIntent.ReturnedToDeck,
        -> state
    }

    internal fun reduce(state: SwipeState, event: SwipeEvent): SwipeState = when (event) {
        is SwipeEvent.PhotosLoaded -> load(state, event)
        is SwipeEvent.LocationLoaded ->
            if (event.location == null || state.deck.none { it.id == event.photoId }) {
                state
            } else {
                state.copy(
                    deck = state.deck
                        .map { if (it.id == event.photoId) it.copy(photo = it.photo.copy(location = event.location)) else it }
                        .toImmutableList(),
                )
            }
        is SwipeEvent.ButtonsModeChanged -> state.copy(buttonsMode = event.enabled)
        is SwipeEvent.DecisionsReloaded -> reloadDecisions(state, event)
    }

    private fun load(state: SwipeState, event: SwipeEvent.PhotosLoaded): SwipeState {
        val deck = event.deck
        val photoIds = deck.mapTo(mutableSetOf()) { it.id }
        // A photo that left the deck cannot be undone
        val undo = event.restoredUndo.filter { it.photoId in photoIds }
        // A fresh visit starts at the first undecided photo, or at the first one when the whole day is decided
        val start = event.restoredIndex?.coerceIn(0, maxOf(deck.lastIndex, 0))
            ?: deck.indexOfFirst { it.decision == null }.takeIf { it >= 0 }
            ?: 0
        return state.copy(
            deck = deck.toImmutableList(),
            trashed = event.trashed.toImmutableList(),
            currentIndex = start,
            undoStack = undo.toImmutableList(),
            isLoading = false,
            isFinished = deck.isEmpty(),
        )
    }

    private fun reloadDecisions(state: SwipeState, event: SwipeEvent.DecisionsReloaded): SwipeState {
        if (state.deck.isEmpty()) return state
        return state.copy(
            deck = state.deck
                .map { photo ->
                    // A photo that was trashed is gone from the deck on the next visit; here it simply has no live decision
                    val decision = event.decisions[photo.id]?.takeIf { it != PhotoDecision.TRASHED && it != PhotoDecision.DELETED }
                    photo.copy(decision = decision)
                }
                .toImmutableList(),
            currentIndex = state.deck.lastIndex,
            // The marks may have changed elsewhere, so what this visit did can no longer be taken back reliably
            undoStack = persistentListOf(),
            isFinished = false,
        )
    }

    private fun swipe(state: SwipeState, direction: SwipeDirection): SwipeState {
        val decision = when (direction) {
            SwipeDirection.Left -> PhotoDecision.DELETE
            SwipeDirection.Right -> PhotoDecision.KEEP
            SwipeDirection.Up, SwipeDirection.Down -> return state
        }
        val current = state.currentPhoto
        if (state.isFinished || current == null) return state
        // Whatever the photo had is overwritten, and what it had is kept for undo
        val deck = state.deck.toPersistentList().set(state.currentIndex, current.copy(decision = decision))
        val nextIndex = indexAfter(deck, state.currentIndex)
        return state.copy(
            deck = deck,
            currentIndex = nextIndex,
            undoStack = state.undoStack.toPersistentList().add(UndoEntry(current.id, current.decision)),
            isFinished = nextIndex >= deck.size,
        )
    }

    /**
     * The next photo is simply the following one, decided or not. After the last photo the deck goes back to the
     * first photo that is still undecided (the user jumped around), and when there is none the deck size is returned,
     * which means the day is completed.
     */
    private fun indexAfter(deck: List<DeckPhoto>, swipedIndex: Int): Int {
        if (swipedIndex < deck.lastIndex) return swipedIndex + 1
        return deck.indexOfFirst { it.decision == null }.takeIf { it >= 0 } ?: deck.size
    }

    private fun undo(state: SwipeState): SwipeState {
        val entry = state.undoStack.lastOrNull() ?: return state
        val remaining = state.undoStack.toPersistentList().removeAt(state.undoStack.lastIndex)
        val index = state.deck.indexOfFirst { it.id == entry.photoId }
        // The photo is gone, so there is nothing to restore; the entry is just dropped
        if (index < 0) return state.copy(undoStack = remaining)
        return state.copy(
            deck = state.deck.toPersistentList().set(index, state.deck[index].copy(decision = entry.previous)),
            currentIndex = index,
            undoStack = remaining,
            isFinished = false,
        )
    }

    private fun jumpTo(state: SwipeState, index: Int): SwipeState =
        if (index !in state.deck.indices) state else state.copy(currentIndex = index, isFinished = false)
}
