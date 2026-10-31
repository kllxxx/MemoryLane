package com.kllxx.memorylane.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kllxx.memorylane.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderNotifierTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)
    private val notifier = ReminderNotifier(context)

    private fun allowNotifications() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    private fun Notification.text(): String = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private fun Notification.title(): String = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    // --- the gate ---

    @Test
    fun `with the permission and the system switch on, the reminder is posted`() {
        allowNotifications()

        assertTrue(notifier.canNotify())
        assertTrue(notifier.post(3))
        assertEquals(1, posted().size)
    }

    @Test
    fun `the system switch off blocks the reminder and no channel is made`() {
        allowNotifications()
        shadowOf(manager).setNotificationsEnabled(false)

        assertFalse(notifier.canNotify())
        assertFalse(notifier.post(3))
        assertTrue(posted().isEmpty())
        assertNull(manager.getNotificationChannel(ReminderConstants.CHANNEL_ID))
    }

    @Test
    fun `from Android 13 a denied permission blocks the reminder`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(notifier.canNotify())
        assertFalse(notifier.post(3))
        assertTrue(posted().isEmpty())
        assertNull(manager.getNotificationChannel(ReminderConstants.CHANNEL_ID))
    }

    @Test
    @Config(sdk = [30])
    fun `before Android 13 there is no permission to ask for`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertTrue(notifier.canNotify())
        assertTrue(notifier.post(3))
        assertEquals(1, posted().size)
    }

    // --- the channel ---

    @Test
    fun `the channel is created with the default importance, its name and its description`() {
        allowNotifications()

        notifier.post(3)

        val channel = manager.getNotificationChannel(ReminderConstants.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals("Daily reminder", channel.name.toString())
        assertEquals("A reminder to sort the photos taken on this day in past years", channel.description)
    }

    @Test
    fun `posting again does not make a second channel`() {
        allowNotifications()

        notifier.post(3)
        notifier.post(4)

        assertEquals(1, manager.notificationChannels.count { it.id == ReminderConstants.CHANNEL_ID })
    }

    // --- the content ---

    @Test
    fun `the title and the text have the count, in the singular and in the plural`() {
        allowNotifications()

        notifier.post(1)
        assertEquals("Photos are waiting", posted().single().title())
        assertEquals("1 photo from past years today", posted().single().text())

        notifier.post(5)
        assertEquals("5 photos from past years today", posted().single().text())
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the Russian text follows the count in every plural form`() {
        allowNotifications()

        listOf(1, 3, 5, 21).forEach { count ->
            notifier.post(count)

            assertEquals("Фото ждут разбора", posted().single().title())
            assertEquals("Сегодня $count фото из прошлых лет", posted().single().text())
        }
    }

    @Test
    fun `a second reminder replaces the first one`() {
        allowNotifications()

        notifier.post(3)
        notifier.post(7)

        val notification = posted().single()
        assertEquals("7 photos from past years today", notification.text())
        assertEquals(ReminderConstants.CHANNEL_ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    // --- the tap ---

    @Test
    fun `a tap starts the main activity with the open-today action and no date`() {
        allowNotifications()
        notifier.post(3)

        val pendingIntent: PendingIntent = posted().single().contentIntent
        val shadow = shadowOf(pendingIntent)
        val intent: Intent = shadow.savedIntent

        assertTrue(shadow.isActivityIntent)
        assertEquals(10, shadow.requestCode)
        assertEquals(ReminderConstants.ACTION_OPEN_TODAY, intent.action)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        val singleTopClearTop = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(singleTopClearTop, intent.flags and singleTopClearTop)
        // The day is worked out at the moment of the tap, so nothing is carried
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
        val immutableUpdate = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        assertEquals(immutableUpdate, shadow.flags and immutableUpdate)
    }
}
