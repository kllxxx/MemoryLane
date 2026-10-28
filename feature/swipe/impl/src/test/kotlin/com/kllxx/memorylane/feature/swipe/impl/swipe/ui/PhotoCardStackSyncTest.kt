package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.haroncode.lazycardstack.LazyCardStackState
import com.haroncode.lazycardstack.rememberLazyCardStackState
import com.haroncode.lazycardstack.swiper.SwipeDirection
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeIntent
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeReducer
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card stack against a state that behaves like the ViewModel: `onIntent` reduces the state with the real
 * [SwipeReducer], so the stack and the state are checked in step the way they run on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class PhotoCardStackSyncTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var stack: LazyCardStackState
    private var state by mutableStateOf(swipeState(count = 5))
    private val intents = mutableListOf<SwipeIntent>()

    private fun show(initial: SwipeState, startIndex: Int = initial.currentIndex, reduce: Boolean = true) {
        state = initial
        composeRule.setContent {
            stack = rememberLazyCardStackState(firstVisibleItemIndex = startIndex)
            MemoryLaneTheme {
                PhotoCardStack(
                    state = state,
                    onIntent = { intent ->
                        intents += intent
                        if (reduce) state = SwipeReducer.reduce(state, intent)
                    },
                    modifier = Modifier.size(300.dp, 420.dp),
                    stack = stack,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun moveStateTo(next: SwipeState) {
        composeRule.runOnIdle { state = next }
        // The write reaches the composition only once the clock has been let through
        composeRule.waitForIdle()
    }

    private fun waitForStack(index: Int) {
        composeRule.waitUntil(timeoutMillis = 10_000) { stack.visibleItemIndex == index }
        composeRule.waitForIdle()
    }

    private fun action(label: String) = composeRule.onNodeWithTag(SwipeTags.CARD).fetchSemanticsNode()
        .config[SemanticsActions.CustomActions].first { it.label == label }

    @Test
    fun `the stack starts on the card of the state`() {
        show(swipeState(count = 5, currentIndex = 2))

        assertEquals(2, stack.visibleItemIndex)
    }

    @Test
    fun `a jump from the strip moves the stack to that card`() {
        show(swipeState(count = 5))

        moveStateTo(state.copy(currentIndex = 3))

        waitForStack(3)
    }

    @Test
    fun `the end-of-deck wrap moves the stack back to the first undecided card`() {
        show(swipeState(count = 5, currentIndex = 4, decisions = mapOf(1L to PhotoDecision.KEEP)))

        moveStateTo(state.copy(currentIndex = 1))

        waitForStack(1)
    }

    @Test
    fun `an undo brings the previous card back`() {
        val decided = swipeState(count = 5, currentIndex = 3, decisions = mapOf(3L to PhotoDecision.DELETE))
        show(decided)

        // The state goes back one card and takes the decision of that card back
        moveStateTo(decided.copy(currentIndex = 2, deck = swipeState(count = 5, currentIndex = 2).deck))

        waitForStack(2)
    }

    @Test
    fun `an action swipe sends one Swipe and the stack and the state end on the next card`() {
        show(swipeState(count = 5))

        composeRule.runOnUiThread { action("Удалить").action() }
        composeRule.waitUntil(timeoutMillis = 10_000) { intents.isNotEmpty() }
        waitForStack(1)

        assertEquals(listOf<SwipeIntent>(SwipeIntent.Swipe(SwipeDirection.Left)), intents)
        assertEquals(1, state.currentIndex)
        assertEquals(PhotoDecision.DELETE, state.deck[0].decision)
    }

    @Test
    fun `a double tap decides one photo, not two`() {
        show(swipeState(count = 5))

        // Looked up once: looking a node up lets the clock run, which would finish the first swipe in between
        val keep = action("Оставить")
        composeRule.runOnUiThread {
            keep.action()
            keep.action()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { intents.isNotEmpty() }
        waitForStack(1)
        composeRule.waitForIdle()

        assertEquals(listOf<SwipeIntent>(SwipeIntent.Swipe(SwipeDirection.Right)), intents)
        assertEquals(listOf(PhotoDecision.KEEP, null, null, null, null), state.deck.map { it.decision })
    }

    @Test
    fun `an undo during a swipe animation leaves the stack and the state on the same card`() {
        // The state is on card 2 and card 1 was decided before it, so an undo can happen at any moment
        val before = swipeState(count = 5, currentIndex = 2, decisions = mapOf(2L to PhotoDecision.KEEP))
        show(before)

        composeRule.runOnUiThread { action("Удалить").action() }
        // The undo arrives while the card is still flying away: the state goes back to card 1
        composeRule.runOnUiThread {
            state = before.copy(currentIndex = 1, deck = swipeState(count = 5, currentIndex = 1).deck)
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()

        // The swipe of card 2 is stale and never decided anything, and the stack is back on the state's card
        assertTrue("stale swipe must be dropped: $intents", intents.none { it is SwipeIntent.Swipe })
        assertEquals(1, state.currentIndex)
        assertEquals(1, stack.visibleItemIndex)
    }

    @Test
    fun `the stack is never ahead of the state after any of these moves`() {
        show(swipeState(count = 6))

        composeRule.runOnUiThread { action("Оставить").action() }
        waitForStack(1)
        moveStateTo(state.copy(currentIndex = 4))
        waitForStack(4)
        composeRule.runOnUiThread { action("Удалить").action() }
        waitForStack(5)

        assertEquals(state.currentIndex.coerceAtMost(5), stack.visibleItemIndex)
    }
}
