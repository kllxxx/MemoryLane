package com.kllxx.memorylane.feature.calendar.impl

import java.time.LocalDate

sealed interface CalendarIntent {
    /** The route was entered: the photos are read again, so the calendar is up to date. */
    data object ScreenEntered : CalendarIntent

    /** Retry after a failed load. */
    data object LoadDays : CalendarIntent
    data object RequestPermission : CalendarIntent
    data class PermissionResult(val granted: Boolean) : CalendarIntent
    data class OpenDay(val date: LocalDate) : CalendarIntent
}
