package com.kllxx.memorylane.feature.calendar.impl

import kotlinx.collections.immutable.toImmutableSet

object CalendarReducer {

    fun reduce(state: CalendarState, intent: CalendarIntent): CalendarState = when (intent) {
        CalendarIntent.LoadDays -> state.copy(error = null)
        CalendarIntent.ScreenEntered,
        CalendarIntent.RequestPermission,
        is CalendarIntent.OpenDay -> state
        is CalendarIntent.PermissionResult -> state.copy(hasPermission = intent.granted)
    }

    internal fun reduce(state: CalendarState, event: CalendarEvent): CalendarState = when (event) {
        is CalendarEvent.IndexLoaded -> state.copy(
            months = event.months,
            phase = if (event.isEmpty) CalendarPhase.Empty else CalendarPhase.Content,
        )
        is CalendarEvent.SortedDatesLoaded -> state.copy(sortedDates = event.sortedDates.toImmutableSet())
        is CalendarEvent.StreakLoaded -> state.copy(currentStreak = event.currentStreak)
        is CalendarEvent.TodayBannerChanged -> state.copy(todayBanner = event.banner)
        CalendarEvent.LoadSucceeded -> state.copy(error = null)
        // The grid does not depend on the photos, so it is shown anyway, with the banner on top
        CalendarEvent.LoadFailed -> state.copy(error = CalendarError.LoadFailed, phase = CalendarPhase.Content)
    }
}
