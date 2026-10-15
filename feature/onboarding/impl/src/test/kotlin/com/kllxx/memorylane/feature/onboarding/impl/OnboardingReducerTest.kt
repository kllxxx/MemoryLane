package com.kllxx.memorylane.feature.onboarding.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingReducerTest {

    private val initial = OnboardingState()

    private fun reduce(state: OnboardingState, intent: OnboardingIntent, denials: Int = 0) =
        OnboardingReducer.reduce(state, intent, denials)

    @Test
    fun `NextPage increments page without reaching last page`() {
        val result = reduce(initial, OnboardingIntent.NextPage)
        assertEquals(1, result.currentPage)
        assertFalse(result.isLastPage)
    }

    @Test
    fun `NextPage onto final page sets isLastPage`() {
        val result = reduce(initial.copy(currentPage = 1), OnboardingIntent.NextPage)
        assertEquals(2, result.currentPage)
        assertTrue(result.isLastPage)
    }

    @Test
    fun `NextPage on last page does not go past the end`() {
        val last = initial.copy(currentPage = 2, isLastPage = true)
        assertEquals(last, reduce(last, OnboardingIntent.NextPage))
    }

    @Test
    fun `PreviousPage decrements page and clears isLastPage`() {
        val result = reduce(initial.copy(currentPage = 2, isLastPage = true), OnboardingIntent.PreviousPage)
        assertEquals(1, result.currentPage)
        assertFalse(result.isLastPage)
    }

    @Test
    fun `PreviousPage on first page stays at zero`() {
        assertEquals(initial, reduce(initial, OnboardingIntent.PreviousPage))
    }

    @Test
    fun `Skip leaves state unchanged`() {
        assertEquals(initial, reduce(initial, OnboardingIntent.Skip))
    }

    @Test
    fun `RequestPermissions sets isLoading`() {
        assertTrue(reduce(initial, OnboardingIntent.RequestPermissions).isLoading)
    }

    @Test
    fun `PermissionResult granted updates flags`() {
        val start = initial.copy(isLoading = true, showPermissionRationale = true)
        val result = reduce(start, OnboardingIntent.PermissionResult(true), denials = 1)
        assertTrue(result.permissionsGranted)
        assertFalse(result.isLoading)
        assertFalse(result.showPermissionRationale)
    }

    @Test
    fun `PermissionResult denied first time shows rationale`() {
        val result = reduce(initial.copy(isLoading = true), OnboardingIntent.PermissionResult(false), denials = 1)
        assertFalse(result.permissionsGranted)
        assertFalse(result.isLoading)
        assertTrue(result.showPermissionRationale)
    }

    @Test
    fun `PermissionResult denied second time hides rationale`() {
        val start = initial.copy(isLoading = true, showPermissionRationale = true)
        val result = reduce(start, OnboardingIntent.PermissionResult(false), denials = 2)
        assertFalse(result.isLoading)
        assertFalse(result.showPermissionRationale)
    }

    @Test
    fun `DismissRationale hides rationale`() {
        val result = reduce(initial.copy(showPermissionRationale = true), OnboardingIntent.DismissRationale)
        assertFalse(result.showPermissionRationale)
    }
}
