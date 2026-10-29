package com.kllxx.memorylane.feature.calendar.impl

import androidx.compose.runtime.Immutable
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private const val DAYS_IN_WEEK = 7
private const val MONTHS_IN_YEAR = 12

/** [rows] are Mon-Sun weeks; null marks the empty cells before day 1 and after the last day. */
@Immutable
data class CalendarMonth(
    val yearMonth: YearMonth,
    val rows: ImmutableList<ImmutableList<DayItem?>>,
)

/**
 * Every month of [year], January first, built from dates only. A month is never left out, whether or not it has
 * photos. Rows are padded so that each has seven cells starting on Monday.
 */
fun buildYearGrid(year: Int): ImmutableList<CalendarMonth> = (1..MONTHS_IN_YEAR).map { month ->
    val yearMonth = YearMonth.of(year, month)
    // Monday = 0 ... Sunday = 6
    val leading = yearMonth.atDay(1).dayOfWeek.value - 1
    val days = (1..yearMonth.lengthOfMonth()).map { DayItem(yearMonth.atDay(it)) }
    val cells = List<DayItem?>(leading) { null } + days
    val trailing = (DAYS_IN_WEEK - cells.size % DAYS_IN_WEEK) % DAYS_IN_WEEK
    CalendarMonth(
        yearMonth = yearMonth,
        rows = (cells + List<DayItem?>(trailing) { null })
            .chunked(DAYS_IN_WEEK) { it.toImmutableList() }
            .toImmutableList(),
    )
}.toImmutableList()

/**
 * The index in the calendar list of the first row of [month]. The list is the weekday header (item 0), then for each
 * month its separator followed by its rows.
 */
fun listIndexOfMonth(months: List<CalendarMonth>, month: YearMonth): Int =
    1 + months.takeWhile { it.yearMonth != month }.sumOf { 1 + it.rows.size }

/**
 * The same grid with the photos of [index] on its days. Days after [today] have not happened yet, so they get
 * none. A month that is not changed by the index is returned as it was.
 */
fun ImmutableList<CalendarMonth>.withMemories(index: OnThisDayIndex?, today: LocalDate): ImmutableList<CalendarMonth> =
    map { month ->
        val rows = month.rows.map { row ->
            row.map { item ->
                if (item == null) {
                    null
                } else {
                    val memories = index?.entries?.get(MonthDay.from(item.date))?.takeIf { !item.date.isAfter(today) }
                    item.copy(memory = memories?.let { DayMemory(it.count, it.cover.uri) })
                }
            }.toImmutableList()
        }.toImmutableList()
        if (rows == month.rows) month else month.copy(rows = rows)
    }.toImmutableList()
