package com.kllxx.memorylane.feature.swipe.impl.calendar

import java.time.YearMonth

private const val DAYS_IN_WEEK = 7

/** [rows] are Mon-Sun weeks; null marks the empty cells before day 1 and after the last day. */
data class CalendarMonth(
    val yearMonth: YearMonth,
    val rows: List<List<DayItem?>>,
) {
    /** At least one day of the month has photos. Days that are not loaded yet do not count. */
    val hasPhotos: Boolean
        get() = rows.any { row -> row.any { it?.loadState is DayLoadState.Loaded } }
}

/** Groups days by month, oldest first, padded so that every row has seven cells starting on Monday. */
fun List<DayItem>.toMonths(): List<CalendarMonth> = groupBy { YearMonth.from(it.date) }
    .toSortedMap()
    .map { (yearMonth, days) ->
        val sorted = days.sortedBy { it.date }
        // Monday = 0 ... Sunday = 6
        val leading = yearMonth.atDay(1).dayOfWeek.value - 1
        val cells = List<DayItem?>(leading) { null } + sorted
        val trailing = (DAYS_IN_WEEK - cells.size % DAYS_IN_WEEK) % DAYS_IN_WEEK
        CalendarMonth(yearMonth, (cells + List<DayItem?>(trailing) { null }).chunked(DAYS_IN_WEEK))
    }

/** Only the months that have photos. */
fun List<DayItem>.toVisibleMonths(): List<CalendarMonth> = toMonths().filter { it.hasPhotos }
