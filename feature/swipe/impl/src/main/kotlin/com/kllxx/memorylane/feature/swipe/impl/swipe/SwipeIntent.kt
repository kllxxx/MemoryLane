package com.kllxx.memorylane.feature.swipe.impl.swipe

sealed interface SwipeIntent {
    data object SwipeLeft : SwipeIntent
    data object SwipeRight : SwipeIntent
    data object Close : SwipeIntent
}
