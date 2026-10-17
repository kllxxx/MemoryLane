package com.kllxx.memorylane.feature.swipe.impl.timeline

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

class TimelineReducerTest {

    private val initial = TimelineState()
    private val d1 = LocalDate.of(2026, 1, 1)
    private val d2 = LocalDate.of(2026, 1, 2)

    private fun skeleton() = listOf(DayItem(d1, null, 0), DayItem(d2, null, 0))

    private fun reduce(state: TimelineState, event: TimelineEvent) =
        TimelineReducer.reduce(state, event)

    private fun reduce(state: TimelineState, intent: TimelineIntent) =
        TimelineReducer.reduce(state, intent)

    @Test
    fun `LoadDays with permission starts loading and clears error`() {
        val result = reduce(initial.copy(hasPermission = true, error = "boom"), TimelineIntent.LoadDays)
        assertTrue(result.isLoading)
        assertNull(result.error)
    }

    @Test
    fun `LoadDays without permission does not start loading but clears error`() {
        val result = reduce(initial.copy(hasPermission = false, error = "boom"), TimelineIntent.LoadDays)
        assertFalse(result.isLoading)
        assertNull(result.error)
    }

    @Test
    fun `PermissionResult updates hasPermission`() {
        assertTrue(reduce(initial, TimelineIntent.PermissionResult(true)).hasPermission)
        assertFalse(
            reduce(initial.copy(hasPermission = true), TimelineIntent.PermissionResult(false)).hasPermission,
        )
    }

    @Test
    fun `RequestPermission OpenDay and LoadRange leave state unchanged`() {
        assertEquals(initial, reduce(initial, TimelineIntent.RequestPermission))
        assertEquals(initial, reduce(initial, TimelineIntent.OpenDay(d1)))
        assertEquals(initial, reduce(initial, TimelineIntent.RefreshDay(d1)))
        assertEquals(initial, reduce(initial, TimelineIntent.LoadRange(d1, d2)))
    }

    @Test
    fun `DaysInitialized sets the skeleton`() {
        val result = reduce(initial, TimelineEvent.DaysInitialized(skeleton()))
        assertEquals(skeleton(), result.days)
    }

    @Test
    fun `DaysLoaded replaces matching days and stops loading`() {
        val photo = Photo(1, mockk<Uri>(relaxed = true), LocalDate.of(2024, 1, 2), 10)
        val state = initial.copy(days = skeleton().toImmutableList(), isLoading = true)
        val loaded = DayItem(d2, photo, 3)

        val result = reduce(state, TimelineEvent.DaysLoaded(listOf(loaded)))

        assertEquals(listOf(DayItem(d1, null, 0), loaded), result.days)
        assertFalse(result.isLoading)
    }

    @Test
    fun `DaysLoaded ignores dates not in the list`() {
        val state = initial.copy(days = skeleton().toImmutableList())
        val stray = DayItem(LocalDate.of(2027, 5, 5), null, 9)
        assertEquals(skeleton(), reduce(state, TimelineEvent.DaysLoaded(listOf(stray))).days)
    }

    @Test
    fun `LoadFailed sets error and stops loading`() {
        val result = reduce(initial.copy(isLoading = true), TimelineEvent.LoadFailed("boom"))
        assertEquals("boom", result.error)
        assertFalse(result.isLoading)
    }
}
