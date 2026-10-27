package com.kllxx.memorylane.feature.swipe.impl.calendar

import com.kllxx.memorylane.core.photos.domain.model.Photo

sealed interface DayLoadState {
    /** Not looked up yet, or the lookup failed. Says nothing about whether the day has photos. */
    data object NotLoaded : DayLoadState

    /** Looked up, and there are no photos on this day. */
    data object Empty : DayLoadState

    data class Loaded(val firstPhoto: Photo, val totalCount: Int) : DayLoadState
}
