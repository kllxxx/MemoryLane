package com.kllxx.memorylane.core.domain.notification

/** What the system currently says about notifications for this app. */
interface NotificationStatusProvider {
    /** Whether the system lets the app show notifications (permission granted and not blocked in the settings). */
    fun areNotificationsEnabled(): Boolean
}
