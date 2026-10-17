package com.kllxx.memorylane.feature.swipe.impl.timeline

import java.time.LocalDate

sealed interface TimelineIntent {
    data object LoadDays : TimelineIntent
    data object RequestPermission : TimelineIntent
    data class PermissionResult(val granted: Boolean) : TimelineIntent
    data class OpenDay(val date: LocalDate) : TimelineIntent

    /** Sent when the user returns from sorting [date], so its cached photo count and thumbnail are reloaded. */
    data class RefreshDay(val date: LocalDate) : TimelineIntent

    /** Sent by the UI when the visible part of the list changes. */
    data class LoadRange(val from: LocalDate, val to: LocalDate) : TimelineIntent
}
