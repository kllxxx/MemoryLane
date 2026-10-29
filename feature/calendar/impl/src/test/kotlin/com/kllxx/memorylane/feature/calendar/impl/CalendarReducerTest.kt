package com.kllxx.memorylane.feature.calendar.impl

import java.time.LocalDate
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarReducerTest {

    private val initial = CalendarState()
    private val d1 = LocalDate.of(2026, 1, 1)
    private val grid = buildYearGrid(2026)

    private fun reduce(state: CalendarState, event: CalendarEvent) =
        CalendarReducer.reduce(state, event)

    private fun reduce(state: CalendarState, intent: CalendarIntent) =
        CalendarReducer.reduce(state, intent)

    @Test
    fun `a new state starts in Loading with no months`() {
        assertEquals(CalendarPhase.Loading, initial.phase)
        assertTrue(initial.months.isEmpty())
    }

    @Test
    fun `LoadDays clears the error`() {
        val result = reduce(initial.copy(hasPermission = true, error = CalendarError.LoadFailed), CalendarIntent.LoadDays)
        assertNull(result.error)
    }

    @Test
    fun `PermissionResult updates hasPermission`() {
        assertTrue(reduce(initial, CalendarIntent.PermissionResult(true)).hasPermission)
        assertFalse(
            reduce(initial.copy(hasPermission = true), CalendarIntent.PermissionResult(false)).hasPermission,
        )
    }

    @Test
    fun `ScreenEntered RequestPermission and OpenDay leave state unchanged`() {
        assertEquals(initial, reduce(initial, CalendarIntent.ScreenEntered))
        assertEquals(initial, reduce(initial, CalendarIntent.RequestPermission))
        assertEquals(initial, reduce(initial, CalendarIntent.OpenDay(d1)))
    }

    @Test
    fun `an index with photos gives Content with its months`() {
        val result = reduce(initial, CalendarEvent.IndexLoaded(grid, isEmpty = false))

        assertEquals(CalendarPhase.Content, result.phase)
        assertEquals(grid, result.months)
    }

    @Test
    fun `an index without photos gives Empty`() {
        val result = reduce(initial, CalendarEvent.IndexLoaded(grid, isEmpty = true))

        assertEquals(CalendarPhase.Empty, result.phase)
    }

    @Test
    fun `a newer index replaces Empty with Content`() {
        val empty = reduce(initial, CalendarEvent.IndexLoaded(grid, isEmpty = true))

        assertEquals(CalendarPhase.Content, reduce(empty, CalendarEvent.IndexLoaded(grid, isEmpty = false)).phase)
    }

    @Test
    fun `SortedDatesLoaded sets the sorted dates`() {
        assertEquals(setOf(d1), reduce(initial, CalendarEvent.SortedDatesLoaded(setOf(d1))).sortedDates)
    }

    @Test
    fun `StreakLoaded sets the streak and leaves the sorted dates`() {
        val result = reduce(reduce(initial, CalendarEvent.SortedDatesLoaded(setOf(d1))), CalendarEvent.StreakLoaded(3))

        assertEquals(3, result.currentStreak)
        assertEquals(setOf(d1), result.sortedDates)
    }

    @Test
    fun `the banner is set and taken away`() {
        val shown = reduce(initial, CalendarEvent.TodayBannerChanged(TodayBanner(count = 4)))

        assertEquals(TodayBanner(4), shown.todayBanner)
        assertEquals(null, reduce(shown, CalendarEvent.TodayBannerChanged(null)).todayBanner)
    }

    @Test
    fun `LoadFailed shows the grid with the error`() {
        val result = reduce(initial.copy(months = grid), CalendarEvent.LoadFailed)

        assertEquals(CalendarError.LoadFailed, result.error)
        assertEquals(CalendarPhase.Content, result.phase)
        assertEquals(grid, result.months)
    }

    @Test
    fun `LoadSucceeded clears the error and keeps the rest`() {
        val failed = reduce(initial.copy(months = persistentListOf()), CalendarEvent.LoadFailed)

        val result = reduce(failed, CalendarEvent.LoadSucceeded)

        assertNull(result.error)
        assertEquals(CalendarPhase.Content, result.phase)
    }
}
