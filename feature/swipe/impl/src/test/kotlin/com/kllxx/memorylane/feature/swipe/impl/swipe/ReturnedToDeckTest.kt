package com.kllxx.memorylane.feature.swipe.impl.swipe

import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.feature.swipe.impl.swipe.ui.swipeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Back from «Перед удалением»: the finished deck comes back on its last photo with the decisions as they are now. */
@RunWith(RobolectricTestRunner::class)
class ReturnedToDeckTest {

    private val finished = swipeState(
        count = 3,
        currentIndex = 3,
        decisions = mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE, 3L to PhotoDecision.DELETE),
    ).copy(isFinished = true)

    private fun reload(state: SwipeState, vararg decisions: Pair<Long, PhotoDecision>) =
        SwipeReducer.reduce(state, SwipeEvent.DecisionsReloaded(mapOf(*decisions)))

    @Test
    fun `the deck is back on its last photo and no longer finished`() {
        val result = reload(finished, 1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE, 3L to PhotoDecision.DELETE)

        assertEquals(2, result.currentIndex)
        assertFalse(result.isFinished)
        assertEquals(3L, result.currentPhoto!!.id)
    }

    @Test
    fun `a mark that was taken back in the review is a kept photo now`() {
        val result = reload(finished, 1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP, 3L to PhotoDecision.DELETE)

        assertEquals(listOf(PhotoDecision.KEEP, PhotoDecision.KEEP, PhotoDecision.DELETE), result.deck.map { it.decision })
    }

    @Test
    fun `a photo without a stored decision is undecided`() {
        val result = reload(finished, 1L to PhotoDecision.KEEP)

        assertEquals(listOf(PhotoDecision.KEEP, null, null), result.deck.map { it.decision })
    }

    @Test
    fun `a trashed or deleted photo has no live decision`() {
        val result = reload(finished, 1L to PhotoDecision.TRASHED, 2L to PhotoDecision.DELETED, 3L to PhotoDecision.KEEP)

        assertEquals(listOf(null, null, PhotoDecision.KEEP), result.deck.map { it.decision })
    }

    @Test
    fun `what the visit did can no longer be undone`() {
        val withUndo = finished.copy(undoStack = kotlinx.collections.immutable.persistentListOf(UndoEntry(3, null)))

        assertTrue(reload(withUndo, 3L to PhotoDecision.DELETE).undoStack.isEmpty())
    }

    @Test
    fun `an empty deck stays as it is`() {
        val empty = swipeState(count = 0)

        assertSame(empty, reload(empty, 1L to PhotoDecision.KEEP))
    }

    @Test
    fun `the intent itself changes nothing, the view model reads the decisions`() {
        assertSame(finished, SwipeReducer.reduce(finished, SwipeIntent.ReturnedToDeck))
    }
}
