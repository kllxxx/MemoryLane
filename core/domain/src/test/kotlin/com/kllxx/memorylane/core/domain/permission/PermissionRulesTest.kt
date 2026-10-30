package com.kllxx.memorylane.core.domain.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRulesTest {

    // --- what exists on which API level ---

    @Test
    fun `the photo permission follows the API level`() {
        assertEquals(PermissionRules.READ_EXTERNAL_STORAGE, PermissionRules.mediaRead(sdkInt = 32))
        assertEquals(PermissionRules.READ_MEDIA_IMAGES, PermissionRules.mediaRead(sdkInt = 33))
        assertEquals(PermissionRules.READ_MEDIA_IMAGES, PermissionRules.mediaRead(sdkInt = 34))
    }

    @Test
    fun `the photo request carries the location permission from API 29`() {
        assertEquals(listOf(PermissionRules.READ_EXTERNAL_STORAGE), PermissionRules.photoRequest(sdkInt = 28))
        assertEquals(
            listOf(PermissionRules.READ_EXTERNAL_STORAGE, PermissionRules.ACCESS_MEDIA_LOCATION),
            PermissionRules.photoRequest(sdkInt = 29),
        )
        assertEquals(
            listOf(PermissionRules.READ_MEDIA_IMAGES, PermissionRules.ACCESS_MEDIA_LOCATION),
            PermissionRules.photoRequest(sdkInt = 34),
        )
    }

    @Test
    fun `a row exists only where its permission does`() {
        assertEquals(listOf(AppPermission.Photos, AppPermission.Delete), PermissionRules.relevant(sdkInt = 28))
        assertEquals(
            listOf(AppPermission.Photos, AppPermission.Location, AppPermission.Delete),
            PermissionRules.relevant(sdkInt = 29),
        )
        assertEquals(listOf(AppPermission.Photos, AppPermission.Location), PermissionRules.relevant(sdkInt = 30))
        assertEquals(
            listOf(AppPermission.Photos, AppPermission.Location, AppPermission.Notifications),
            PermissionRules.relevant(sdkInt = 33),
        )
    }

    @Test
    fun `the delete permission is only there up to API 29`() {
        assertEquals(PermissionRules.WRITE_EXTERNAL_STORAGE, PermissionRules.permissionOf(AppPermission.Delete, sdkInt = 24))
        assertEquals(PermissionRules.WRITE_EXTERNAL_STORAGE, PermissionRules.permissionOf(AppPermission.Delete, sdkInt = 29))
        assertNull(PermissionRules.permissionOf(AppPermission.Delete, sdkInt = 30))
    }

    @Test
    fun `the notification permission is only there from API 33`() {
        assertNull(PermissionRules.permissionOf(AppPermission.Notifications, sdkInt = 32))
        assertEquals(PermissionRules.POST_NOTIFICATIONS, PermissionRules.permissionOf(AppPermission.Notifications, sdkInt = 33))
    }

    @Test
    fun `the photo and location rows request both, the others only their own permission`() {
        assertEquals(PermissionRules.photoRequest(sdkInt = 34), PermissionRules.requestFor(AppPermission.Photos, sdkInt = 34))
        assertEquals(PermissionRules.photoRequest(sdkInt = 34), PermissionRules.requestFor(AppPermission.Location, sdkInt = 34))
        assertEquals(
            listOf(PermissionRules.POST_NOTIFICATIONS),
            PermissionRules.requestFor(AppPermission.Notifications, sdkInt = 34),
        )
        assertEquals(emptyList<String>(), PermissionRules.requestFor(AppPermission.Delete, sdkInt = 34))
    }

    // --- request or settings ---

    @Test
    fun `a permission never asked for can be requested`() {
        assertTrue(PermissionRules.canRequest(granted = false, requestedBefore = false, shouldShowRationale = false))
    }

    @Test
    fun `a permission denied once can be requested while the system wants a rationale`() {
        assertTrue(PermissionRules.canRequest(granted = false, requestedBefore = true, shouldShowRationale = true))
    }

    @Test
    fun `a permission denied for good needs the settings`() {
        assertFalse(PermissionRules.canRequest(granted = false, requestedBefore = true, shouldShowRationale = false))
    }

    @Test
    fun `a granted permission is never requested`() {
        assertFalse(PermissionRules.canRequest(granted = true, requestedBefore = false, shouldShowRationale = false))
    }

    // --- the state ---

    @Test
    fun `the state answers per permission`() {
        val state = PermissionsState(photos = true, location = false, notifications = true, delete = false)

        assertTrue(state.isGranted(AppPermission.Photos))
        assertFalse(state.isGranted(AppPermission.Location))
        assertTrue(state.isGranted(AppPermission.Notifications))
        assertFalse(state.isGranted(AppPermission.Delete))
    }
}
