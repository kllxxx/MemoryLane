package com.kllxx.memorylane.feature.swipe.impl.swipe

import android.content.IntentSender

sealed interface SwipeEffect {
    data object NavigateToTimeline : SwipeEffect
    data class RequestSystemDelete(val intentSender: IntentSender) : SwipeEffect
}
