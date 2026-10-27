package com.kllxx.memorylane.feature.swipe.impl.calendar

import java.time.LocalDate

sealed interface CalendarIntent {
    data object LoadDays : CalendarIntent
    data object RequestPermission : CalendarIntent
    data class PermissionResult(val granted: Boolean) : CalendarIntent
    data class OpenDay(val date: LocalDate) : CalendarIntent

    /** Sent when the user returns from sorting [date], so its cached photo count and thumbnail are reloaded. */
    data class RefreshDay(val date: LocalDate) : CalendarIntent

    /** Sent by the UI when the visible part of the list changes. */
    data class LoadRange(val from: LocalDate, val to: LocalDate) : CalendarIntent
}
