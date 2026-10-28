package com.kllxx.memorylane.feature.swipe.impl.swipe

import com.haroncode.lazycardstack.swiper.SwipeDirection

sealed interface SwipeIntent {
    /** [SwipeDirection.Left] marks the photo for deletion and [SwipeDirection.Right] keeps it; other directions do nothing. */
    data class Swipe(val direction: SwipeDirection) : SwipeIntent

    data object Undo : SwipeIntent

    /** Makes the photo at [index] of the deck the current one. */
    data class JumpTo(val index: Int) : SwipeIntent

    data object ToggleButtonsMode : SwipeIntent
    data object Share : SwipeIntent
    data object OpenInGallery : SwipeIntent
    data object Close : SwipeIntent
}
