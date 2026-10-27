package com.kllxx.memorylane.feature.swipe.impl.calendar

import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

object CalendarReducer {

    fun reduce(state: CalendarState, intent: CalendarIntent): CalendarState = when (intent) {
        // Without permission nothing is loaded, so nothing would ever clear the spinner
        CalendarIntent.LoadDays -> state.copy(isLoading = state.hasPermission, error = null)
        is CalendarIntent.LoadRange,
        CalendarIntent.RequestPermission,
        is CalendarIntent.OpenDay,
        is CalendarIntent.RefreshDay -> state
        is CalendarIntent.PermissionResult -> state.copy(hasPermission = intent.granted)
    }

    internal fun reduce(state: CalendarState, event: CalendarEvent): CalendarState = when (event) {
        is CalendarEvent.DaysInitialized -> state.copy(days = event.days.toImmutableList())
        is CalendarEvent.DaysLoaded -> {
            val loaded = event.days.associateBy { it.date }
            state.copy(
                days = state.days.map { loaded[it.date] ?: it }.toImmutableList(),
                isLoading = false,
            )
        }
        is CalendarEvent.StatsLoaded -> state.copy(
            sortedDates = event.sortedDates.toImmutableSet(),
            currentStreak = event.currentStreak,
        )
        is CalendarEvent.LoadFailed -> state.copy(error = CalendarError.LoadFailed, isLoading = false)
    }
}
