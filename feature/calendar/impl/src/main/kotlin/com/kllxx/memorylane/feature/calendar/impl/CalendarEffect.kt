package com.kllxx.memorylane.feature.calendar.impl

import java.time.LocalDate

sealed interface CalendarEffect {
    data class NavigateToSwipe(val date: LocalDate) : CalendarEffect

    /** Opens the Swipe deck of the photos without a date. */
    data object NavigateToUndated : CalendarEffect
    data object RequestPermissionLauncher : CalendarEffect
}
