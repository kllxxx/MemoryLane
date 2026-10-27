package com.kllxx.memorylane.feature.swipe.impl.calendar

import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList

/** Results produced by [CalendarViewModel] itself. The UI never sends these. */
internal sealed interface CalendarEvent {
    /** The photo index arrived: [months] is the whole grid with the photos on it, [isEmpty] tells if there are none. */
    data class IndexLoaded(val months: ImmutableList<CalendarMonth>, val isEmpty: Boolean) : CalendarEvent
    data class StatsLoaded(val sortedDates: Set<LocalDate>, val currentStreak: Int) : CalendarEvent

    /** A refresh finished: whatever failed before is over. */
    data object LoadSucceeded : CalendarEvent
    data object LoadFailed : CalendarEvent
}
