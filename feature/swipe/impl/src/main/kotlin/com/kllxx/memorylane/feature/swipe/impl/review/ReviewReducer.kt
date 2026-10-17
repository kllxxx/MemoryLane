package com.kllxx.memorylane.feature.swipe.impl.review

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

object ReviewReducer {

    fun reduce(state: ReviewState, intent: ReviewIntent): ReviewState = when (intent) {
        ReviewIntent.LoadStats -> state
        ReviewIntent.ConfirmDelete ->
            if (state.isDeleting || state.isFinished) state else state.copy(isDeleting = true)
        ReviewIntent.SystemDeleteConfirmed ->
            // Counted once: a repeated confirmation must not add the freed bytes again
            if (state.isFinished) {
                state
            } else {
                state.copy(
                    pendingDeleteIds = persistentListOf(),
                    totalFreedBytes = state.totalFreedBytes + state.sessionFreedBytes,
                    isDeleting = false,
                    isFinished = true,
                )
            }
        ReviewIntent.SystemDeleteCancelled -> state.copy(isDeleting = false)
    }

    internal fun reduce(state: ReviewState, event: ReviewEvent): ReviewState = when (event) {
        is ReviewEvent.StatsLoaded -> state.copy(
            keptCount = event.keptCount,
            deletedCount = event.deletedCount,
            pendingDeleteIds = event.pendingDeleteIds.toImmutableList(),
            totalFreedBytes = event.totalFreedBytes,
            sessionFreedBytes = event.sessionFreedBytes,
        )
    }
}
