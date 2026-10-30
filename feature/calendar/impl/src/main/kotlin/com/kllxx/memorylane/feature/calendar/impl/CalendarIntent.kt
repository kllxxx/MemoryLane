package com.kllxx.memorylane.feature.calendar.impl

import java.time.LocalDate

sealed interface CalendarIntent {
    /** The route was entered: the photos are read again, so the calendar is up to date. */
    data object ScreenEntered : CalendarIntent

    /** Retry after a failed load. */
    data object LoadDays : CalendarIntent
    data object RequestPermission : CalendarIntent

    /**
     * Sent on every ON_RESUME, which also follows a permission dialog or the system settings: what the system says about
     * the photo permission, which only the screen can see. [shouldShowRationale]: denied, but the prompt can come again.
     * [partial]: "select photos" was chosen, which is no access.
     */
    data class PermissionChecked(val shouldShowRationale: Boolean, val partial: Boolean) : CalendarIntent

    data class PermissionResult(val granted: Boolean) : CalendarIntent
    data class OpenDay(val date: LocalDate) : CalendarIntent

    /** The banner of the photos without a date was tapped. */
    data object OpenUndated : CalendarIntent
}
