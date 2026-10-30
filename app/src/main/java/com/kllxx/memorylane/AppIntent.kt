package com.kllxx.memorylane

sealed interface AppIntent {
    /** The reminder was tapped, at a cold start or while the app runs. */
    data object ReminderOpened : AppIntent
}
