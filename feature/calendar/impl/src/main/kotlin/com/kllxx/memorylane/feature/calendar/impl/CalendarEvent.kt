package com.kllxx.memorylane.feature.calendar.impl

import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList

/** Results produced by [CalendarViewModel] itself. The UI never sends these. */
sealed interface CalendarEvent {
    /** The photo index arrived: [months] is the whole grid with the photos on it, [isEmpty] tells if there are none. */
    data class IndexLoaded(val months: ImmutableList<CalendarMonth>, val isEmpty: Boolean) : CalendarEvent
    /** The date changed: [months] is the grid for it, of the new year if the year changed. */
    data class TodayChanged(val today: LocalDate, val months: ImmutableList<CalendarMonth>) : CalendarEvent
    data class SortedDatesLoaded(val sortedDates: Set<LocalDate>) : CalendarEvent
    data class StreakLoaded(val currentStreak: Int) : CalendarEvent
    data class TodayBannerChanged(val banner: TodayBanner?) : CalendarEvent

    data class UndatedBannerChanged(val banner: UndatedBanner?) : CalendarEvent

    /** A refresh finished: whatever failed before is over. */
    data object LoadSucceeded : CalendarEvent
    data object LoadFailed : CalendarEvent
}
