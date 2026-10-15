package com.kllxx.memorylane.feature.swipe.impl.swipe

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
        is SwipeEvent.PhotosLoaded -> state.copy(
            photos = event.photos.toImmutableList(),
            isLoading = false,
            isFinished = event.photos.isEmpty(),
        )
    }

    private fun advance(state: SwipeState, delete: Boolean): SwipeState {
        val photo = state.photos.getOrNull(state.currentIndex)
        if (state.isFinished || photo == null) return state
        val nextIndex = state.currentIndex + 1
        return state.copy(
            currentIndex = nextIndex,
            pendingDeletes = if (delete) (state.pendingDeletes + photo.id).toImmutableList() else state.pendingDeletes,
            isFinished = nextIndex >= state.photos.size,
        )
    }
}
