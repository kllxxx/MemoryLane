package com.kllxx.memorylane.feature.swipe.impl.timeline

import java.time.LocalDate

sealed interface TimelineEffect {
    data class NavigateToSwipe(val date: LocalDate) : TimelineEffect
    data object RequestPermissionLauncher : TimelineEffect
}
