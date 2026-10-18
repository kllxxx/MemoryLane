package com.kllxx.memorylane.feature.swipe.impl.timeline

import android.net.Uri
import com.kllxx.memorylane.core.photos.domain.model.Photo
import io.mockk.mockk
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMonthsTest {

    private fun days(year: Int, month: Int, state: DayLoadState = DayLoadState.NotLoaded): List<DayItem> {
        val first = LocalDate.of(year, month, 1)
        return (1..first.lengthOfMonth()).map { DayItem(first.withDayOfMonth(it), state) }
    }

    private val loaded = DayLoadState.Loaded(Photo(1, mockk<Uri>(relaxed = true), LocalDate.of(2024, 1, 1), 1), 2)

    @Test
    fun `a month starting on Monday has no leading blanks`() {
        // June 2026 starts on a Monday
        val month = days(2026, 6).toMonths().single()
        assertEquals(LocalDate.of(2026, 6, 1), month.rows.first().first()?.date)
    }

    @Test
    fun `a month starting on Sunday has six leading blanks`() {
        // March 2026 starts on a Sunday
        val first = days(2026, 3).toMonths().single().rows.first()
        assertEquals(6, first.take(6).count { it == null })
        assertEquals(LocalDate.of(2026, 3, 1), first[6]?.date)
    }

    @Test
    fun `September 2026 starts on Tuesday`() {
        val first = days(2026, 9).toMonths().single().rows.first()
        assertNull(first[0])
        assertEquals(LocalDate.of(2026, 9, 1), first[1]?.date)
    }

    @Test
    fun `every row has seven cells and the last row is padded`() {
        val month = days(2026, 9).toMonths().single()
        assertTrue(month.rows.all { it.size == 7 })
        // 1 leading blank + 30 days = 31 cells, padded to 35
        assertEquals(5, month.rows.size)
        assertEquals(4, month.rows.last().count { it == null })
    }

    @Test
    fun `leap February keeps its 29th day`() {
        val month = days(2028, 2).toMonths().single()
        assertEquals(29, month.rows.flatten().count { it != null })
    }

    @Test
    fun `months are ordered oldest first`() {
        val months = (days(2026, 10) + days(2026, 1) + days(2026, 5)).toMonths()
        assertEquals(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 5), YearMonth.of(2026, 10)), months.map { it.yearMonth })
    }

    @Test
    fun `only months with photos are shown, whatever the load state of the others`() {
        val all = days(2026, 1, DayLoadState.Empty) + days(2026, 2, DayLoadState.NotLoaded) + days(2026, 3, loaded)
        assertEquals(listOf(YearMonth.of(2026, 3)), all.toVisibleMonths().map { it.yearMonth })
    }

    @Test
    fun `a month that is not loaded yet is not shown`() {
        assertTrue(days(2026, 6, DayLoadState.NotLoaded).toVisibleMonths().isEmpty())
    }

    @Test
    fun `a month appears as soon as one of its days has photos`() {
        val loading = days(2026, 6, DayLoadState.NotLoaded)
        assertTrue(loading.toVisibleMonths().isEmpty())
        val oneLoaded = loading.map { if (it.date.dayOfMonth == 12) it.copy(loadState = loaded) else it }
        assertEquals(listOf(YearMonth.of(2026, 6)), oneLoaded.toVisibleMonths().map { it.yearMonth })
    }

    @Test
    fun `a month with a single photo day stays visible`() {
        val mixed = days(2026, 4, DayLoadState.Empty).map {
            if (it.date.dayOfMonth == 9) it.copy(loadState = loaded) else it
        }
        assertEquals(1, mixed.toVisibleMonths().size)
    }

    @Test
    fun `no days gives no months`() {
        assertTrue(emptyList<DayItem>().toVisibleMonths().isEmpty())
    }
}
