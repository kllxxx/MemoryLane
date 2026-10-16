package com.kllxx.memorylane.feature.swipe.impl.swipe

import com.kllxx.memorylane.core.photos.domain.model.Photo
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

object SwipeReducer {

    fun reduce(state: SwipeState, intent: SwipeIntent): SwipeState = when (intent) {
        is SwipeIntent.LoadPhotos -> state.copy(
            photos = persistentListOf(),
            currentIndex = 0,
            pendingDeletes = persistentListOf(),
            isLoading = true,
            isFinished = false,
            date = intent.date,
        )
        SwipeIntent.SwipeRight -> advance(state, delete = false)
        SwipeIntent.SwipeLeft -> advance(state, delete = true)
        SwipeIntent.Close -> state
    }

    internal fun reduce(state: SwipeState, event: SwipeEvent): SwipeState = when (event) {
        is SwipeEvent.PhotosLoaded -> {
            // Resume: photos already marked for deletion on an earlier run are not shown again
            val photoIds = event.photos.mapTo(mutableSetOf()) { it.id }
            val pending = event.pendingDeleteIds.filter { it in photoIds }.distinct()
            val firstToShow = firstIndexToShow(event.photos, from = 0, pending = pending)
            state.copy(
                photos = event.photos.toImmutableList(),
                pendingDeletes = pending.toImmutableList(),
                currentIndex = firstToShow,
                isLoading = false,
                isFinished = firstToShow >= event.photos.size,
            )
        }
    }

    private fun advance(state: SwipeState, delete: Boolean): SwipeState {
        val photo = state.photos.getOrNull(state.currentIndex)
        if (state.isFinished || photo == null) return state
        val pending = when {
            delete && photo.id !in state.pendingDeletes -> state.pendingDeletes + photo.id
            // Keeping a photo that was marked earlier takes the mark back
            !delete && photo.id in state.pendingDeletes -> state.pendingDeletes - photo.id
            else -> state.pendingDeletes
        }
        val nextIndex = firstIndexToShow(state.photos, from = state.currentIndex + 1, pending = pending)
        return state.copy(
            currentIndex = nextIndex,
            pendingDeletes = pending.toImmutableList(),
            isFinished = nextIndex >= state.photos.size,
        )
    }

    /** Index of the first photo at or after [from] that is not in [pending], or the list size. */
    private fun firstIndexToShow(photos: List<Photo>, from: Int, pending: List<Long>): Int {
        val index = (from until photos.size).firstOrNull { photos[it].id !in pending }
        return index ?: photos.size
    }
}
