package com.kllxx.memorylane.feature.swipe.impl.timeline

import kotlinx.collections.immutable.toImmutableList

object TimelineReducer {

    fun reduce(state: TimelineState, intent: TimelineIntent): TimelineState = when (intent) {
        TimelineIntent.LoadDays -> state.copy(isLoading = true, error = null)
        is TimelineIntent.LoadRange,
        TimelineIntent.RequestPermission,
        is TimelineIntent.OpenDay -> state
        is TimelineIntent.PermissionResult -> state.copy(hasPermission = intent.granted)
    }

    internal fun reduce(state: TimelineState, event: TimelineEvent): TimelineState = when (event) {
        is TimelineEvent.DaysInitialized -> state.copy(days = event.days.toImmutableList())
        is TimelineEvent.DaysLoaded -> {
            val loaded = event.days.associateBy { it.date }
            state.copy(
                days = state.days.map { loaded[it.date] ?: it }.toImmutableList(),
                isLoading = false,
            )
        }
        is TimelineEvent.LoadFailed -> state.copy(error = event.message, isLoading = false)
    }
}
