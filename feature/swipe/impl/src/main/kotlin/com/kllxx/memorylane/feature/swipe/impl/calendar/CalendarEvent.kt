package com.kllxx.memorylane.feature.swipe.impl.calendar

import java.time.LocalDate

/** Results produced by [CalendarViewModel] itself. The UI never sends these. */
internal sealed interface CalendarEvent {
    data class DaysInitialized(val days: List<DayItem>) : CalendarEvent
    data class DaysLoaded(val days: List<DayItem>) : CalendarEvent
    data class StatsLoaded(val sortedDates: Set<LocalDate>, val currentStreak: Int) : CalendarEvent
    data object LoadFailed : CalendarEvent
}
