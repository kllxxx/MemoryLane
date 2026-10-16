package com.kllxx.memorylane.feature.swipe.impl.swipe

import java.time.LocalDate

sealed interface SwipeEffect {
    data object NavigateToTimeline : SwipeEffect

    /**
     * All photos are swiped and some are marked for deletion on [date].
     * [totalPhotosCount] is how many photos the session had, so Review can derive the kept count.
     */
    data class NavigateToReview(val date: LocalDate, val totalPhotosCount: Int) : SwipeEffect
}
