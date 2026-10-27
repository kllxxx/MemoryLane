package com.kllxx.memorylane.feature.swipe.impl.calendar

import android.net.Uri
import com.kllxx.memorylane.core.photos.domain.model.Photo
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarReducerTest {

    private val initial = CalendarState()
    private val d1 = LocalDate.of(2026, 1, 1)
    private val d2 = LocalDate.of(2026, 1, 2)

    private fun skeleton() = listOf(DayItem(d1, DayLoadState.NotLoaded), DayItem(d2, DayLoadState.NotLoaded))

    private fun reduce(state: CalendarState, event: CalendarEvent) =
        CalendarReducer.reduce(state, event)

    private fun reduce(state: CalendarState, intent: CalendarIntent) =
        CalendarReducer.reduce(state, intent)

    @Test
    fun `LoadDays with permission starts loading and clears error`() {
        val result = reduce(initial.copy(hasPermission = true, error = CalendarError.LoadFailed), CalendarIntent.LoadDays)
        assertTrue(result.isLoading)
        assertNull(result.error)
    }

    @Test
    fun `LoadDays without permission does not start loading but clears error`() {
        val result = reduce(initial.copy(hasPermission = false, error = CalendarError.LoadFailed), CalendarIntent.LoadDays)
        assertFalse(result.isLoading)
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
    fun `RequestPermission OpenDay and LoadRange leave state unchanged`() {
        assertEquals(initial, reduce(initial, CalendarIntent.RequestPermission))
        assertEquals(initial, reduce(initial, CalendarIntent.OpenDay(d1)))
        assertEquals(initial, reduce(initial, CalendarIntent.RefreshDay(d1)))
        assertEquals(initial, reduce(initial, CalendarIntent.LoadRange(d1, d2)))
    }

    @Test
    fun `DaysInitialized sets the skeleton`() {
        val result = reduce(initial, CalendarEvent.DaysInitialized(skeleton()))
        assertEquals(skeleton(), result.days)
    }

    @Test
    fun `DaysLoaded replaces matching days and stops loading`() {
        val photo = Photo(1, mockk<Uri>(relaxed = true), LocalDate.of(2024, 1, 2), 10)
        val state = initial.copy(days = skeleton().toImmutableList(), isLoading = true)
        val loaded = DayItem(d2, DayLoadState.Loaded(photo, 3))

        val result = reduce(state, CalendarEvent.DaysLoaded(listOf(loaded)))

        assertEquals(listOf(DayItem(d1, DayLoadState.NotLoaded), loaded), result.days)
        assertFalse(result.isLoading)
    }

    @Test
    fun `DaysLoaded ignores dates not in the list`() {
        val state = initial.copy(days = skeleton().toImmutableList())
        val stray = DayItem(LocalDate.of(2027, 5, 5), DayLoadState.Empty)
        assertEquals(skeleton(), reduce(state, CalendarEvent.DaysLoaded(listOf(stray))).days)
    }

    @Test
    fun `LoadFailed sets error and stops loading`() {
        val result = reduce(initial.copy(isLoading = true), CalendarEvent.LoadFailed)
        assertEquals(CalendarError.LoadFailed, result.error)
        assertFalse(result.isLoading)
    }

    @Test
    fun `StatsLoaded sets sorted dates and streak`() {
        val result = reduce(initial, CalendarEvent.StatsLoaded(setOf(d1, d2), currentStreak = 4))
        assertEquals(setOf(d1, d2), result.sortedDates)
        assertEquals(4, result.currentStreak)
    }
}
