package com.kllxx.memorylane.feature.swipe.impl.review

import android.content.IntentSender

sealed interface ReviewEffect {
    data class RequestSystemDelete(val intentSender: IntentSender) : ReviewEffect
    data object ShowFreedSpaceAnimation : ReviewEffect
    data object NavigateToTimeline : ReviewEffect

    /** The delete failed with an error, as opposed to the user cancelling the dialog. */
    data object ShowDeleteError : ReviewEffect
}
