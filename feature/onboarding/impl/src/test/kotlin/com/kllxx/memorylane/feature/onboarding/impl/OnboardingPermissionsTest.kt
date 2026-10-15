package com.kllxx.memorylane.feature.onboarding.impl

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPermissionsTest {

    @Test
    fun `API 33+ requests images, media location and notifications`() {
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
            OnboardingPermissions.required(sdkInt = 34),
        )
    }

    @Test
    fun `API 29 to 32 requests external storage and media location without notifications`() {
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION),
            OnboardingPermissions.required(sdkInt = 30),
        )
    }

    @Test
    fun `below API 29 requests only external storage`() {
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            OnboardingPermissions.required(sdkInt = 28),
        )
    }

    @Test
    fun `media access is granted when the read permission is granted even if location is denied`() {
        val result = mapOf(
            Manifest.permission.READ_MEDIA_IMAGES to true,
            Manifest.permission.ACCESS_MEDIA_LOCATION to false,
        )
        assertTrue(OnboardingPermissions.isMediaAccessGranted(result, sdkInt = 34))
    }

    @Test
    fun `media access is granted when only notifications are denied`() {
        val result = mapOf(
            Manifest.permission.READ_MEDIA_IMAGES to true,
            Manifest.permission.ACCESS_MEDIA_LOCATION to true,
            Manifest.permission.POST_NOTIFICATIONS to false,
        )
        assertTrue(OnboardingPermissions.isMediaAccessGranted(result, sdkInt = 34))
    }

    @Test
    fun `media access is denied when the read permission is denied`() {
        val result = mapOf(
            Manifest.permission.READ_MEDIA_IMAGES to false,
            Manifest.permission.ACCESS_MEDIA_LOCATION to true,
        )
        assertFalse(OnboardingPermissions.isMediaAccessGranted(result, sdkInt = 34))
    }

    @Test
    fun `media access is denied for an empty result`() {
        assertFalse(OnboardingPermissions.isMediaAccessGranted(emptyMap(), sdkInt = 30))
    }
}
