package com.kllxx.memorylane.feature.calendar.impl

import java.time.LocalDate

sealed interface CalendarEffect {
    data class NavigateToSwipe(val date: LocalDate) : CalendarEffect

    /** Opens the Swipe deck of the photos without a date. */
    data object NavigateToUndated : CalendarEffect
    data object RequestPermissionLauncher : CalendarEffect

    /** The system will not ask again: the person can only give the access in the settings of the app. */
    data object OpenAppSettings : CalendarEffect
}
