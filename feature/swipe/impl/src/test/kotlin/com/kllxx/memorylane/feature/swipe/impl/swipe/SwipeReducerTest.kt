package com.kllxx.memorylane.feature.swipe.impl.swipe

import com.kllxx.memorylane.core.common.UiError
import com.kllxx.memorylane.core.domain.model.DeckKey
import android.net.Uri
import com.haroncode.lazycardstack.swiper.SwipeDirection
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.photos.domain.model.Photo
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeReducerTest {

    private val date = LocalDate.of(2026, 10, 15)
    private val uri = mockk<Uri>(relaxed = true)

    private fun deckPhoto(id: Long, decision: PhotoDecision? = null) = DeckPhoto(
        photo = Photo(id, uri, LocalDateTime.of(2024, 10, 15, 10, 0).plusMinutes(id), sizeBytes = id * 100),
        decision = decision,
        takenAtText = "photo $id",
    )

    /** A deck of [count] photos; [decisions] are by photo id. */
    private fun deck(count: Int, vararg decisions: Pair<Long, PhotoDecision>): List<DeckPhoto> {
        val byId = decisions.toMap()
        return (1..count).map { deckPhoto(it.toLong(), byId[it.toLong()]) }
    }

    private fun loaded(
        count: Int,
        vararg decisions: Pair<Long, PhotoDecision>,
        trashed: List<DeckPhoto> = emptyList(),
        restoredIndex: Int? = null,
        restoredUndo: List<UndoEntry> = emptyList(),
    ): SwipeState = reduce(
        SwipeState(deckKey = DeckKey.Day(date), isLoading = true),
        SwipeEvent.PhotosLoaded(deck(count, *decisions), trashed, restoredIndex, restoredUndo),
    )

    private fun reduce(state: SwipeState, intent: SwipeIntent) = SwipeReducer.reduce(state, intent)

    private fun reduce(state: SwipeState, event: SwipeEvent) = SwipeReducer.reduce(state, event)

    private fun SwipeState.swipe(direction: SwipeDirection) = reduce(this, SwipeIntent.Swipe(direction))

    private fun SwipeState.decisions() = deck.map { it.decision }

    // --- loading and resume ---

    @Test
    fun `PhotosLoaded stores the deck and stops loading`() {
        val result = loaded(2)

        assertEquals(deck(2), result.deck)
        assertEquals(0, result.currentIndex)
        assertFalse(result.isLoading)
        assertFalse(result.isFinished)
    }

    @Test
    fun `a fresh visit starts at the first undecided photo`() {
        val result = loaded(5, 1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE)

        assertEquals(2, result.currentIndex)
    }

    @Test
    fun `a decided photo in the middle does not stop the resume from finding the first undecided one`() {
        val result = loaded(4, 1L to PhotoDecision.KEEP, 3L to PhotoDecision.KEEP)

        assertEquals(1, result.currentIndex)
    }

    @Test
    fun `a day where everything is decided starts at the first photo and is completed`() {
        val result = loaded(3, 1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE, 3L to PhotoDecision.KEEP)

        assertEquals(0, result.currentIndex)
        assertTrue(result.isDayCompleted)
        assertFalse(result.isFinished)
        assertEquals(3, result.deck.size)
    }

    @Test
    fun `a day with an undecided photo is not completed`() {
        assertFalse(loaded(3, 1L to PhotoDecision.KEEP).isDayCompleted)
    }

    @Test
    fun `an empty deck is finished and not completed`() {
        val result = loaded(0)

        assertTrue(result.isFinished)
        assertFalse(result.isDayCompleted)
    }

    @Test
    fun `a restored index wins over the first undecided photo`() {
        val result = loaded(5, 1L to PhotoDecision.KEEP, restoredIndex = 3)

        assertEquals(3, result.currentIndex)
    }

    @Test
    fun `a restored index is kept inside the deck`() {
        assertEquals(2, loaded(3, restoredIndex = 9).currentIndex)
        assertEquals(0, loaded(3, restoredIndex = -4).currentIndex)
    }

    @Test
    fun `the restored undo stack is kept, without the photos that left the deck`() {
        val result = loaded(
            2,
            restoredUndo = listOf(
                UndoEntry(1, null),
                UndoEntry(99, PhotoDecision.KEEP),
                UndoEntry(2, PhotoDecision.DELETE),
            ),
        )

        assertEquals(listOf(UndoEntry(1, null), UndoEntry(2, PhotoDecision.DELETE)), result.undoStack)
        assertTrue(result.canUndo)
    }

    @Test
    fun `trashed photos are kept apart from the deck`() {
        val trashed = listOf(deckPhoto(50, PhotoDecision.TRASHED))

        val result = loaded(2, trashed = trashed)

        assertEquals(trashed, result.trashed)
        assertEquals(listOf(1L, 2L), result.deck.map { it.id })
    }

    // --- swiping ---

    @Test
    fun `swiping left marks the photo for deletion and moves to the next one`() {
        val result = loaded(3).swipe(SwipeDirection.Left)

        assertEquals(listOf(PhotoDecision.DELETE, null, null), result.decisions())
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun `swiping right keeps the photo and moves to the next one`() {
        val result = loaded(3).swipe(SwipeDirection.Right)

        assertEquals(listOf(PhotoDecision.KEEP, null, null), result.decisions())
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun `up and down swipes do nothing`() {
        val state = loaded(3)

        assertSame(state, state.swipe(SwipeDirection.Up))
        assertSame(state, state.swipe(SwipeDirection.Down))
    }

    @Test
    fun `the next photo is the following one even when it already has a decision`() {
        val result = loaded(3, 2L to PhotoDecision.DELETE).swipe(SwipeDirection.Right)

        assertEquals(1, result.currentIndex)
        assertEquals(PhotoDecision.DELETE, result.deck[1].decision)
    }

    @Test
    fun `swiping a decided photo overwrites its decision`() {
        val deleted = loaded(2, 1L to PhotoDecision.DELETE, 2L to PhotoDecision.KEEP)

        val result = reduce(deleted, SwipeIntent.JumpTo(0)).swipe(SwipeDirection.Right)

        assertEquals(PhotoDecision.KEEP, result.deck[0].decision)
    }

    @Test
    fun `swiping the last photo with an undecided one before it goes back to that one`() {
        val state = loaded(4, 2L to PhotoDecision.KEEP, 3L to PhotoDecision.KEEP)
        val onLast = reduce(state, SwipeIntent.JumpTo(3))

        val result = onLast.swipe(SwipeDirection.Right)

        assertEquals(0, result.currentIndex)
        assertFalse(result.isFinished)
    }

    @Test
    fun `swiping the last photo goes to the first undecided one, not the nearest`() {
        val state = loaded(5, 1L to PhotoDecision.KEEP, 3L to PhotoDecision.KEEP, 4L to PhotoDecision.KEEP)
        val onLast = reduce(state, SwipeIntent.JumpTo(4))

        assertEquals(1, onLast.swipe(SwipeDirection.Left).currentIndex)
    }

    @Test
    fun `swiping the last photo when everything is decided finishes the day`() {
        val state = loaded(2, 1L to PhotoDecision.KEEP)
        val onLast = reduce(state, SwipeIntent.JumpTo(1))

        val result = onLast.swipe(SwipeDirection.Left)

        assertTrue(result.isFinished)
        assertTrue(result.isDayCompleted)
        assertEquals(2, result.currentIndex)
    }

    @Test
    fun `swiping a middle photo that completes the day does not finish it`() {
        val state = loaded(3, 1L to PhotoDecision.KEEP, 3L to PhotoDecision.KEEP)

        val result = state.swipe(SwipeDirection.Right)

        assertTrue(result.isDayCompleted)
        assertFalse(result.isFinished)
        assertEquals(2, result.currentIndex)
    }

    @Test
    fun `a finished day cannot be swiped again`() {
        val finished = loaded(1).swipe(SwipeDirection.Right)

        assertTrue(finished.isFinished)
        assertSame(finished, finished.swipe(SwipeDirection.Left))
    }

    @Test
    fun `swiping with only trashed photos changes nothing, because they can never be swiped`() {
        val state = loaded(0, trashed = listOf(deckPhoto(50, PhotoDecision.TRASHED)))

        assertSame(state, state.swipe(SwipeDirection.Left))
        assertEquals(PhotoDecision.TRASHED, state.trashed.single().decision)
    }

    // --- jump ---

    @Test
    fun `jumping makes that photo the current one`() {
        val result = reduce(loaded(5), SwipeIntent.JumpTo(3))

        assertEquals(3, result.currentIndex)
    }

    @Test
    fun `jumping, then swiping, overwrites the decision and continues from there`() {
        val state = loaded(5, 4L to PhotoDecision.DELETE)

        val result = reduce(state, SwipeIntent.JumpTo(3)).swipe(SwipeDirection.Right)

        assertEquals(PhotoDecision.KEEP, result.deck[3].decision)
        assertEquals(4, result.currentIndex)
    }

    @Test
    fun `jumping outside the deck is ignored`() {
        val state = loaded(3)

        assertSame(state, reduce(state, SwipeIntent.JumpTo(3)))
        assertSame(state, reduce(state, SwipeIntent.JumpTo(-1)))
    }

    @Test
    fun `jumping changes no decision and does not touch the undo stack`() {
        val state = loaded(3).swipe(SwipeDirection.Left)

        val result = reduce(state, SwipeIntent.JumpTo(2))

        assertEquals(state.decisions(), result.decisions())
        assertEquals(state.undoStack, result.undoStack)
    }

    // --- undo ---

    @Test
    fun `there is nothing to undo at first`() {
        val state = loaded(3)

        assertFalse(state.canUndo)
        assertSame(state, reduce(state, SwipeIntent.Undo))
    }

    @Test
    fun `a swipe can be undone`() {
        val state = loaded(3).swipe(SwipeDirection.Left)

        assertTrue(state.canUndo)
    }

    @Test
    fun `undo restores no decision and makes the photo the current one again`() {
        val swiped = loaded(3).swipe(SwipeDirection.Left)

        val result = reduce(swiped, SwipeIntent.Undo)

        assertEquals(listOf(null, null, null), result.decisions())
        assertEquals(0, result.currentIndex)
        assertFalse(result.canUndo)
    }

    @Test
    fun `undo restores the decision the photo had before`() {
        val state = loaded(2, 1L to PhotoDecision.DELETE, 2L to PhotoDecision.KEEP)
        val overwritten = reduce(state, SwipeIntent.JumpTo(0)).swipe(SwipeDirection.Right)
        assertEquals(PhotoDecision.KEEP, overwritten.deck[0].decision)

        val result = reduce(overwritten, SwipeIntent.Undo)

        assertEquals(PhotoDecision.DELETE, result.deck[0].decision)
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun `several undos in a row go back one swipe at a time`() {
        val state = loaded(4).swipe(SwipeDirection.Left).swipe(SwipeDirection.Right).swipe(SwipeDirection.Left)
        assertEquals(listOf(PhotoDecision.DELETE, PhotoDecision.KEEP, PhotoDecision.DELETE, null), state.decisions())

        val once = reduce(state, SwipeIntent.Undo)
        assertEquals(listOf(PhotoDecision.DELETE, PhotoDecision.KEEP, null, null), once.decisions())
        assertEquals(2, once.currentIndex)
        assertTrue(once.canUndo)

        val twice = reduce(once, SwipeIntent.Undo)
        assertEquals(listOf(PhotoDecision.DELETE, null, null, null), twice.decisions())
        assertEquals(1, twice.currentIndex)

        val thrice = reduce(twice, SwipeIntent.Undo)
        assertEquals(listOf(null, null, null, null), thrice.decisions())
        assertEquals(0, thrice.currentIndex)
        assertFalse(thrice.canUndo)

        assertSame(thrice, reduce(thrice, SwipeIntent.Undo))
    }

    @Test
    fun `undo after a jump goes back to the photo that was swiped`() {
        val state = reduce(loaded(5), SwipeIntent.JumpTo(3)).swipe(SwipeDirection.Left)
        assertEquals(4, state.currentIndex)

        val result = reduce(state, SwipeIntent.Undo)

        assertEquals(3, result.currentIndex)
        assertNull(result.deck[3].decision)
    }

    @Test
    fun `undo after the last photo finished the day brings the deck back`() {
        val finished = loaded(1).swipe(SwipeDirection.Right)
        assertTrue(finished.isFinished)

        val result = reduce(finished, SwipeIntent.Undo)

        assertFalse(result.isFinished)
        assertEquals(0, result.currentIndex)
        assertNull(result.deck[0].decision)
    }

    @Test
    fun `an undo entry of a photo that left the deck is dropped without changing the deck`() {
        val state = loaded(2).copy(undoStack = listOf(UndoEntry(99, PhotoDecision.KEEP)).toImmutableList())

        val result = reduce(state, SwipeIntent.Undo)

        assertEquals(state.deck, result.deck)
        assertEquals(state.currentIndex, result.currentIndex)
        assertFalse(result.canUndo)
    }

    // --- locations and buttons mode ---

    @Test
    fun `LocationLoaded puts the place on that photo only`() {
        val result = reduce(loaded(2), SwipeEvent.LocationLoaded(2L, "Paris, France"))

        assertNull(result.deck[0].photo.location)
        assertEquals("Paris, France", result.deck[1].photo.location)
    }

    @Test
    fun `LocationLoaded keeps the decision and the text of the photo`() {
        val state = loaded(2, 2L to PhotoDecision.KEEP)

        val result = reduce(state, SwipeEvent.LocationLoaded(2L, "Paris, France"))

        assertEquals(PhotoDecision.KEEP, result.deck[1].decision)
        assertEquals("photo 2", result.deck[1].takenAtText)
    }

    @Test
    fun `LocationLoaded without a place changes nothing`() {
        val state = loaded(2)

        assertSame(state, reduce(state, SwipeEvent.LocationLoaded(1L, null)))
    }

    @Test
    fun `LocationLoaded for a photo that is not in the deck changes nothing`() {
        val state = loaded(2)

        assertSame(state, reduce(state, SwipeEvent.LocationLoaded(99L, "Paris, France")))
    }

    @Test
    fun `ButtonsModeChanged sets the mode`() {
        val state = loaded(1)

        assertTrue(reduce(state, SwipeEvent.ButtonsModeChanged(true)).buttonsMode)
        assertFalse(reduce(reduce(state, SwipeEvent.ButtonsModeChanged(true)), SwipeEvent.ButtonsModeChanged(false)).buttonsMode)
    }

    @Test
    fun `the intents the ViewModel handles leave the state alone`() {
        val state = loaded(2)

        assertSame(state, reduce(state, SwipeIntent.ToggleButtonsMode))
        assertSame(state, reduce(state, SwipeIntent.Share))
        assertSame(state, reduce(state, SwipeIntent.OpenInGallery))
        assertSame(state, reduce(state, SwipeIntent.Close))
    }

    @Test
    fun `a failed load stops the spinner and shows the error`() {
        val state = SwipeState(deckKey = DeckKey.Day(date), isLoading = true)

        val result = reduce(state, SwipeEvent.LoadFailed(UiError.Generic))

        assertFalse(result.isLoading)
        assertEquals(UiError.Generic, result.loadError)
        assertTrue(result.deck.isEmpty())
    }

    @Test
    fun `retry clears the error and shows the spinner`() {
        val failed = SwipeState(deckKey = DeckKey.Day(date), isLoading = false, loadError = UiError.Generic)

        val result = reduce(failed, SwipeIntent.Retry)

        assertTrue(result.isLoading)
        assertNull(result.loadError)
    }
}
