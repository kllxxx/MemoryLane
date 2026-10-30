package com.kllxx.memorylane

sealed interface AppIntent {
    /** The reminder was tapped, at a cold start or while the app runs. */
    data object ReminderOpened : AppIntent

    /** The app came to the foreground: the permissions may have been changed in the system settings meanwhile. */
    data object Resumed : AppIntent
}
