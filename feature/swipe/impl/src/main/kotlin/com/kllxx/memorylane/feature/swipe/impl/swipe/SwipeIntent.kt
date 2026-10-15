package com.kllxx.memorylane.feature.swipe.impl.swipe

import java.time.LocalDate

sealed interface SwipeIntent {
    data class LoadPhotos(val date: LocalDate) : SwipeIntent
    data object SwipeLeft : SwipeIntent
    data object SwipeRight : SwipeIntent
    data object Close : SwipeIntent
}
