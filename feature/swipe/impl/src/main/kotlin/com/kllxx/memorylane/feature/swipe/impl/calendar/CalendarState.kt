package com.kllxx.memorylane.feature.swipe.impl.calendar

import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

data class CalendarState(
    val days: ImmutableList<DayItem> = persistentListOf(),
    val today: LocalDate = LocalDate.EPOCH,
    /** On This Day dates the user has finished sorting. */
    val sortedDates: ImmutableSet<LocalDate> = persistentSetOf(),
    val currentStreak: Int = 0,
    val isLoading: Boolean = false,
    val hasPermission: Boolean = false,
    val error: CalendarError? = null,
)
