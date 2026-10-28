package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import com.haroncode.lazycardstack.swiper.SwipeDirection
import com.kllxx.memorylane.core.domain.model.PhotoDecision

/** How the card stack has to move to show the card the state is on. */
enum class StackMove {
    /** The stack is already there: the normal case after a gesture, which advances the stack itself. */
    None,

    /** One card back, animated: an undo. */
    Back,

    /** A jump from the strip, the end-of-deck wrap to the first undecided photo, or a repair of a desync. */
    Snap,
}

/** The state's current index is the truth; [stackIndex] is only ever made to follow [target]. */
fun planMove(stackIndex: Int, target: Int): StackMove = when (target) {
    stackIndex -> StackMove.None
    stackIndex - 1 -> StackMove.Back
    else -> StackMove.Snap
}

/** The side a card is brought back from: the side it left through, which its decision tells. */
fun directionOf(decision: PhotoDecision?): SwipeDirection =
    if (decision == PhotoDecision.DELETE) SwipeDirection.Left else SwipeDirection.Right

/**
 * Lets a swipe through only once, and only for the card the state is on. A swipe from a card the state has already
 * left (an in-flight swipe whose card was undone meanwhile) is stale. A second swipe of the same card is a duplicate:
 * a drag that interrupts the fly-out animation can report the same card twice before the state has advanced, and
 * the second one would decide the next photo.
 */
class SwipeGate {
    private var latch = NONE

    /** True if a swipe from the card at [index] may become an intent, given that the state is on [currentIndex]. */
    fun accept(index: Int, currentIndex: Int): Boolean {
        if (index != currentIndex || index == latch) return false
        latch = index
        return true
    }

    /** The state moved on, so the next card may be swiped. */
    fun reset() {
        latch = NONE
    }

    private companion object {
        const val NONE = -1
    }
}
