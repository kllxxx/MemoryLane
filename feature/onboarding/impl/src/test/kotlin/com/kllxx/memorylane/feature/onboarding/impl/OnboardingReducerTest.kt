package com.kllxx.memorylane.feature.onboarding.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingReducerTest {

    private val initial = OnboardingState()
    private val photoPage = initial.copy(currentPage = OnboardingConstants.PHOTO_PAGE)

    private fun reduce(state: OnboardingState, intent: OnboardingIntent, denials: Int = 0) =
        OnboardingReducer.reduce(state, intent, denials)

    @Test
    fun `there are four pages`() {
        assertEquals(4, initial.totalPages)
    }

    @Test
    fun `NextPage increments page without reaching last page`() {
        val result = reduce(initial, OnboardingIntent.NextPage)
        assertEquals(1, result.currentPage)
        assertFalse(result.isLastPage)
    }

    @Test
    fun `NextPage onto final page sets isLastPage`() {
        val result = reduce(photoPage, OnboardingIntent.NextPage)
        assertEquals(3, result.currentPage)
        assertTrue(result.isLastPage)
    }

    @Test
    fun `NextPage on last page does not go past the end`() {
        val last = initial.copy(currentPage = 3, isLastPage = true)
        assertEquals(last, reduce(last, OnboardingIntent.NextPage))
    }

    @Test
    fun `PreviousPage decrements page and clears isLastPage`() {
        val result = reduce(initial.copy(currentPage = 3, isLastPage = true), OnboardingIntent.PreviousPage)
        assertEquals(2, result.currentPage)
        assertFalse(result.isLastPage)
    }

    @Test
    fun `PreviousPage on first page stays at zero`() {
        assertEquals(initial, reduce(initial, OnboardingIntent.PreviousPage))
    }

    @Test
    fun `Skip and OpenSettings leave state unchanged`() {
        assertEquals(initial, reduce(initial, OnboardingIntent.Skip))
        assertEquals(initial, reduce(initial, OnboardingIntent.OpenSettings))
    }

    @Test
    fun `permission requests set isLoading`() {
        assertTrue(reduce(photoPage, OnboardingIntent.RequestPhotoPermission).isLoading)
        assertTrue(reduce(photoPage, OnboardingIntent.RequestNotificationPermission).isLoading)
    }

    @Test
    fun `photo permission granted moves to the notification page`() {
        val start = photoPage.copy(isLoading = true, showPermissionRationale = true)
        val result = reduce(start, OnboardingIntent.PhotoPermissionResult(true), denials = 1)
        assertTrue(result.permissionsGranted)
        assertFalse(result.isLoading)
        assertFalse(result.showPermissionRationale)
        assertEquals(OnboardingConstants.NOTIFICATION_PAGE, result.currentPage)
        assertTrue(result.isLastPage)
    }

    @Test
    fun `photo permission denied below the threshold stays on the page without rationale`() {
        val result = reduce(photoPage.copy(isLoading = true), OnboardingIntent.PhotoPermissionResult(false), denials = 1)
        assertFalse(result.permissionsGranted)
        assertFalse(result.isLoading)
        assertFalse(result.showPermissionRationale)
        assertEquals(OnboardingConstants.PHOTO_PAGE, result.currentPage)
    }

    @Test
    fun `photo permission denied at the threshold shows rationale`() {
        val result = reduce(
            photoPage.copy(isLoading = true),
            OnboardingIntent.PhotoPermissionResult(false),
            denials = OnboardingConstants.PERMISSION_DENIAL_THRESHOLD,
        )
        assertFalse(result.isLoading)
        assertTrue(result.showPermissionRationale)
        assertEquals(OnboardingConstants.PHOTO_PAGE, result.currentPage)
    }

    @Test
    fun `notification result only clears loading`() {
        val start = initial.copy(currentPage = 3, isLastPage = true, isLoading = true)
        assertEquals(start.copy(isLoading = false), reduce(start, OnboardingIntent.NotificationPermissionResult(false)))
        assertEquals(start.copy(isLoading = false), reduce(start, OnboardingIntent.NotificationPermissionResult(true)))
    }

    @Test
    fun `DismissRationale hides rationale and moves on`() {
        val result = reduce(photoPage.copy(showPermissionRationale = true), OnboardingIntent.DismissRationale)
        assertFalse(result.showPermissionRationale)
        assertEquals(OnboardingConstants.NOTIFICATION_PAGE, result.currentPage)
    }
}
