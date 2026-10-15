package com.kllxx.memorylane.feature.swipe.impl.review

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

object ReviewReducer {

    fun reduce(state: ReviewState, intent: ReviewIntent): ReviewState = when (intent) {
        ReviewIntent.LoadStats -> state
        ReviewIntent.ConfirmDelete ->
            if (state.isDeleting || state.isFinished) state else state.copy(isDeleting = true)
        is ReviewIntent.DeleteResult ->
            if (intent.success) {
                state.copy(
                    pendingDeleteIds = persistentListOf(),
                    sessionFreedBytes = intent.freedBytes,
                    totalFreedBytes = state.totalFreedBytes + intent.freedBytes,
                    isDeleting = false,
                    isFinished = true,
                )
            } else {
                state.copy(isDeleting = false)
            }
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
