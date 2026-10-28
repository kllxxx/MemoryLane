package com.kllxx.memorylane.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Debug builds only: posts the reminder right away so that a tap on it can be tried without waiting for the alarm.
 * The count can be given with `--ei count N`.
 */
@AndroidEntryPoint
class DebugReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: ReminderNotifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_POST_REMINDER) return
        notifier.post(intent.getIntExtra(EXTRA_COUNT, DEFAULT_COUNT))
    }

    companion object {
        const val ACTION_POST_REMINDER = "com.kllxx.memorylane.debug.POST_REMINDER"
        const val EXTRA_COUNT = "count"
        private const val DEFAULT_COUNT = 5
    }
}
