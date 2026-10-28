package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import com.haroncode.lazycardstack.swiper.SwipeDirection
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StackSyncTest {

    @Test
    fun `a stack that is on the card of the state does not move`() {
        assertEquals(StackMove.None, planMove(stackIndex = 3, target = 3))
    }

    @Test
    fun `one card back is an undo, animated`() {
        assertEquals(StackMove.Back, planMove(stackIndex = 3, target = 2))
    }

    @Test
    fun `any other change is a jump, not animated`() {
        assertEquals(StackMove.Snap, planMove(stackIndex = 3, target = 0))
        assertEquals(StackMove.Snap, planMove(stackIndex = 3, target = 7))
        // The wrap to the first undecided photo after the last one
        assertEquals(StackMove.Snap, planMove(stackIndex = 9, target = 1))
    }

    @Test
    fun `one card forward is a jump too, because a gesture advances the stack itself`() {
        // The state getting ahead of the stack only happens when something went wrong, so it is repaired
        assertEquals(StackMove.Snap, planMove(stackIndex = 3, target = 4))
    }

    @Test
    fun `a card that was deleted comes back from the left and every other from the right`() {
        assertEquals(SwipeDirection.Left, directionOf(PhotoDecision.DELETE))
        assertEquals(SwipeDirection.Right, directionOf(PhotoDecision.KEEP))
        assertEquals(SwipeDirection.Right, directionOf(null))
    }

    // --- the gate ---

    @Test
    fun `a swipe of the card the state is on is let through`() {
        assertTrue(SwipeGate().accept(index = 2, currentIndex = 2))
    }

    @Test
    fun `a swipe of a card the state has left is stale and dropped`() {
        val gate = SwipeGate()

        assertFalse(gate.accept(index = 2, currentIndex = 1))
        assertFalse(gate.accept(index = 2, currentIndex = 3))
    }

    @Test
    fun `a second swipe of the same card is a duplicate and dropped`() {
        val gate = SwipeGate()

        assertTrue(gate.accept(index = 2, currentIndex = 2))
        // A drag that interrupted the fly-out animation reports the card again before the state advanced
        assertFalse(gate.accept(index = 2, currentIndex = 2))
    }

    @Test
    fun `the next card can be swiped once the state has moved on`() {
        val gate = SwipeGate()
        assertTrue(gate.accept(index = 2, currentIndex = 2))

        gate.reset()

        assertTrue(gate.accept(index = 3, currentIndex = 3))
    }

    @Test
    fun `after an undo the same card can be swiped again`() {
        val gate = SwipeGate()
        assertTrue(gate.accept(index = 2, currentIndex = 2))

        // The state went to 3 and back to 2 through an undo
        gate.reset()

        assertTrue(gate.accept(index = 2, currentIndex = 2))
    }

    @Test
    fun `a fast double swipe decides one photo only`() {
        val gate = SwipeGate()
        var currentIndex = 0
        val decided = mutableListOf<Int>()
        fun swipe(index: Int) {
            if (gate.accept(index, currentIndex)) {
                decided += index
                currentIndex++
            }
        }

        // The same card reported twice, then the next one once the state moved on
        swipe(0)
        swipe(0)
        gate.reset()
        swipe(1)

        assertEquals(listOf(0, 1), decided)
    }
}
