package com.kllxx.memorylane.core.data.notification

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationStatusProviderImplTest {

    private val context = RuntimeEnvironment.getApplication()
    private val provider = NotificationStatusProviderImpl(context)

    private fun setSystemEnabled(enabled: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationsEnabled(enabled)
    }

    @Test
    fun `reports notifications as enabled when the system allows them`() {
        setSystemEnabled(true)

        assertTrue(provider.areNotificationsEnabled())
    }

    @Test
    fun `reports notifications as disabled when the system blocks them`() {
        setSystemEnabled(false)

        assertFalse(provider.areNotificationsEnabled())
    }
}
