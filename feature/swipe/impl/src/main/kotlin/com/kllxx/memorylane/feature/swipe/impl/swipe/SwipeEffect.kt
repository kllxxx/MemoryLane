package com.kllxx.memorylane.feature.swipe.impl.swipe

import java.time.LocalDate

sealed interface SwipeEffect {
    data object NavigateToTimeline : SwipeEffect

    /** All photos are swiped and some are marked for deletion on [date]. */
    data class NavigateToReview(val date: LocalDate) : SwipeEffect
}
