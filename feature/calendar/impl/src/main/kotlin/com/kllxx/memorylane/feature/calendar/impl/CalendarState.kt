package com.kllxx.memorylane.feature.calendar.impl

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** What the calendar body shows. */
enum class CalendarPhase {
    /** The photos of the gallery are not known yet: only the header is shown. */
    Loading,

    /** The gallery has no photo from an earlier year, so there is nothing to look back on. */
    Empty,

    /** The grid of the whole year, with the photos on the days that have them. */
    Content,
}

/** Today has photos from earlier years and is not completed yet: [count] is how many, as the calendar tile has it. */
@Immutable
data class TodayBanner(val count: Int)

@Immutable
data class CalendarState(
    /** Every month of the current year. Built from dates only, so it exists before the photos are known. */
    val months: ImmutableList<CalendarMonth> = persistentListOf(),
    val today: LocalDate = LocalDate.EPOCH,
    val phase: CalendarPhase = CalendarPhase.Loading,
    /** On This Day dates the user has finished sorting. */
    val sortedDates: ImmutableSet<LocalDate> = persistentSetOf(),
    val currentStreak: Int = 0,
    /** Null when today has no photos or is completed; the first item of the list while it is there. */
    val todayBanner: TodayBanner? = null,
    val hasPermission: Boolean = false,
    val error: CalendarError? = null,
)
