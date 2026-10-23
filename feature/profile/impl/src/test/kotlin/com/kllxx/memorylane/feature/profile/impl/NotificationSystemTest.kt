package com.kllxx.memorylane.feature.profile.impl

import android.content.Intent
import android.os.Build
import android.provider.Settings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationSystemTest {

    private val tiramisu = Build.VERSION_CODES.TIRAMISU

    @Test
    fun `permission can be requested when it was never asked`() {
        assertTrue(NotificationSystem.canRequestPermission(tiramisu, granted = false, permissionRequested = false, shouldShowRationale = false))
    }

    @Test
    fun `permission can be requested again while the system wants a rationale`() {
        assertTrue(NotificationSystem.canRequestPermission(tiramisu, granted = false, permissionRequested = true, shouldShowRationale = true))
    }

    @Test
    fun `permission is permanently denied after a request without a rationale`() {
        assertFalse(NotificationSystem.canRequestPermission(tiramisu, granted = false, permissionRequested = true, shouldShowRationale = false))
    }

    @Test
    fun `a granted permission is not requested`() {
        assertFalse(NotificationSystem.canRequestPermission(tiramisu, granted = true, permissionRequested = false, shouldShowRationale = false))
    }

    @Test
    fun `there is no runtime permission before API 33`() {
        assertFalse(
            NotificationSystem.canRequestPermission(
                Build.VERSION_CODES.TIRAMISU - 1,
                granted = false,
                permissionRequested = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `settings intent on API 26 and later opens the notification settings`() {
        val intent = NotificationSystem.settingsIntent("com.kllxx.memorylane", Build.VERSION_CODES.O)

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals("com.kllxx.memorylane", intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun `settings intent on API 24 and 25 opens the app details`() {
        val intent = NotificationSystem.settingsIntent("com.kllxx.memorylane", Build.VERSION_CODES.N_MR1)

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:com.kllxx.memorylane", intent.dataString)
    }

    @Test
    fun `support mail goes to the address with the subject and body`() {
        val intent = supportMailIntent("help@example.com", "Subject", "Body")

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:help@example.com", intent.dataString)
        assertArrayEquals(arrayOf("help@example.com"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("Subject", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Body", intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
