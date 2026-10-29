package com.kllxx.memorylane.feature.calendar.impl

import android.net.Uri
import com.kllxx.memorylane.core.photos.domain.model.DayCover
import com.kllxx.memorylane.core.photos.domain.model.DayMemories
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import io.mockk.mockk
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthsTest {

    private val uri = mockk<Uri>(relaxed = true)

    private fun month(year: Int, month: Int) = buildYearGrid(year)[month - 1]

    private fun memories(count: Int) = DayMemories(count, DayCover(1, uri, LocalDate.of(2020, 1, 1)))

    private fun index(vararg entries: Pair<MonthDay, DayMemories>) = OnThisDayIndex(2026, mapOf(*entries))

    @Test
    fun `the grid has all 12 months of the year in order`() {
        val grid = buildYearGrid(2026)

        assertEquals((1..12).map { YearMonth.of(2026, it) }, grid.map { it.yearMonth })
    }

    @Test
    fun `the grid is built from dates only`() {
        val days = buildYearGrid(2026).flatMap { it.rows }.flatten().filterNotNull()

        assertEquals(365, days.size)
        assertTrue(days.all { it.memory == null })
        assertEquals(LocalDate.of(2026, 1, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 12, 31), days.last().date)
    }

    @Test
    fun `a leap year has 366 days and a Feb 29`() {
        val days = buildYearGrid(2028).flatMap { it.rows }.flatten().filterNotNull()

        assertEquals(366, days.size)
        assertTrue(days.any { it.date == LocalDate.of(2028, 2, 29) })
    }

    @Test
    fun `a month starting on Monday has no leading blanks`() {
        // June 2026 starts on a Monday
        assertEquals(LocalDate.of(2026, 6, 1), month(2026, 6).rows.first().first()?.date)
    }

    @Test
    fun `a month starting on Sunday has six leading blanks`() {
        // March 2026 starts on a Sunday
        val first = month(2026, 3).rows.first()
        assertEquals(6, first.take(6).count { it == null })
        assertEquals(LocalDate.of(2026, 3, 1), first[6]?.date)
    }

    @Test
    fun `September 2026 starts on Tuesday`() {
        val first = month(2026, 9).rows.first()
        assertNull(first[0])
        assertEquals(LocalDate.of(2026, 9, 1), first[1]?.date)
    }

    @Test
    fun `every row has seven cells and the last row is padded`() {
        val month = month(2026, 9)
        assertTrue(month.rows.all { it.size == 7 })
        // 1 leading blank + 30 days = 31 cells, padded to 35
        assertEquals(5, month.rows.size)
        assertEquals(4, month.rows.last().count { it == null })
    }

    @Test
    fun `memories land on the month and day of every year`() {
        val grid = buildYearGrid(2026).withMemories(index(MonthDay.of(3, 9) to memories(4)), today = LocalDate.of(2026, 12, 31))

        val march9 = grid[2].rows.flatten().filterNotNull().single { it.date == LocalDate.of(2026, 3, 9) }
        assertEquals(4, march9.memory?.count)
        assertEquals(uri, march9.memory?.coverUri)
        assertEquals(1, grid.flatMap { it.rows }.flatten().filterNotNull().count { it.memory != null })
    }

    @Test
    fun `months without photos stay in the grid`() {
        val grid = buildYearGrid(2026).withMemories(index(MonthDay.of(3, 9) to memories(1)), today = LocalDate.of(2026, 12, 31))

        assertEquals(12, grid.size)
        assertTrue(grid[0].rows.flatten().filterNotNull().all { it.memory == null })
    }

    @Test
    fun `days after today get no memory`() {
        val today = LocalDate.of(2026, 3, 1)
        val grid = buildYearGrid(2026).withMemories(index(MonthDay.of(3, 9) to memories(1)), today)

        assertNull(grid[2].rows.flatten().filterNotNull().single { it.date == LocalDate.of(2026, 3, 9) }.memory)
    }

    @Test
    fun `Feb 29 appears only in a leap year grid`() {
        val index = index(MonthDay.of(2, 29) to memories(2))

        val leap = buildYearGrid(2028).withMemories(index, today = LocalDate.of(2028, 12, 31))
        val plain = buildYearGrid(2026).withMemories(index, today = LocalDate.of(2026, 12, 31))

        assertNotNull(leap[1].rows.flatten().filterNotNull().single { it.date == LocalDate.of(2028, 2, 29) }.memory)
        assertTrue(plain.flatMap { it.rows }.flatten().filterNotNull().all { it.memory == null })
    }

    @Test
    fun `an unchanged month is the same instance after applying an index`() {
        val base = buildYearGrid(2026)
        val first = base.withMemories(index(MonthDay.of(3, 9) to memories(1)), today = LocalDate.of(2026, 12, 31))
        val second = first.withMemories(index(MonthDay.of(3, 9) to memories(1)), today = LocalDate.of(2026, 12, 31))

        // Equal months are equal values, so the list rows are not recomposed
        assertEquals(first, second)
        assertSame(first[0].yearMonth, second[0].yearMonth)
    }

    @Test
    fun `no index leaves the grid without photos`() {
        val grid = buildYearGrid(2026).withMemories(null, today = LocalDate.of(2026, 12, 31))

        assertEquals(buildYearGrid(2026), grid)
    }

    @Test
    fun `the first month starts right after the weekday header`() {
        val grid = buildYearGrid(2026)

        // item 0 is the weekday header, item 1 the January separator
        assertEquals(1, listIndexOfMonth(grid, YearMonth.of(2026, 1)))
    }

    @Test
    fun `each month is a separator plus its rows`() {
        val grid = buildYearGrid(2026)

        val january = grid[0].rows.size
        assertEquals(1 + 1 + january, listIndexOfMonth(grid, YearMonth.of(2026, 2)))
        val beforeOctober = grid.take(9).sumOf { 1 + it.rows.size }
        assertEquals(1 + beforeOctober, listIndexOfMonth(grid, YearMonth.of(2026, 10)))
    }
}
