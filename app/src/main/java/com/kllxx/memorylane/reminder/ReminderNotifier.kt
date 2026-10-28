package com.kllxx.memorylane.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kllxx.memorylane.MainActivity
import com.kllxx.memorylane.R
import com.kllxx.memorylane.core.ui.R as UiR
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The names the reminder is told apart by. */
object ReminderConstants {
    /** The action of the intent a tap on the reminder starts [MainActivity] with. */
    const val ACTION_OPEN_TODAY = "com.kllxx.memorylane.action.OPEN_TODAY"

    const val CHANNEL_ID = "reminder"
    const val NOTIFICATION_ID = 1

    /** Sent to [ReminderReceiver] when the time of the reminder has come. */
    const val ACTION_REMIND = "com.kllxx.memorylane.action.REMIND"
}

/**
 * The daily reminder as a notification: «Фото ждут разбора», with how many photos of today there are. A tap opens the
 * app with [ReminderConstants.ACTION_OPEN_TODAY], which the app turns into Swipe for today when there is something to
 * review (see `AppViewModel.onReminderOpened`).
 */
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Whether a notification can be shown at all: the system switch and, from Android 13, the permission. */
    fun canNotify(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                )

    /** Shows the reminder for [photoCount] photos; shown again it replaces the one that is there. Returns false when it could not be shown. */
    @SuppressLint("MissingPermission") // checked by canNotify()
    fun post(photoCount: Int): Boolean {
        if (!canNotify()) return false
        ensureChannel()
        val notification = NotificationCompat.Builder(context, ReminderConstants.CHANNEL_ID)
            .setSmallIcon(UiR.drawable.ic_nav_calendar_filled)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.resources.getQuantityString(R.plurals.reminder_text, photoCount, photoCount))
            .setContentIntent(openTodayIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(ReminderConstants.NOTIFICATION_ID, notification)
        return true
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(ReminderConstants.CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderConstants.CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.reminder_channel_description) },
        )
    }

    /**
     * Brings the running app to the front instead of starting a second one: with SINGLE_TOP and CLEAR_TOP an activity
     * that is already there gets `onNewIntent`, and one that is not is started, so there is one activity either way.
     */
    private fun openTodayIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ReminderConstants.ACTION_OPEN_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_TODAY,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val REQUEST_OPEN_TODAY = 10
    }
}
