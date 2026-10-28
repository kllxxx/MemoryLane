package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeState
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.BEHIND_PEEK
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.BEHIND_SCALE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card behind the top card is a hint of the deck: it never leaves the footprint of the top card, apart from a thin
 * edge above it, and it takes its own shape only as the top card is dragged away. The pair that used to look like two
 * photos on top of each other is a landscape top card with a portrait card behind it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class BehindCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val landscape = 4f / 3f
    private val portrait = 3f / 4f
    private val tolerance = 1f

    private fun deck(vararg ratios: Float): SwipeState =
        swipeState(count = ratios.size, aspectRatios = ratios.mapIndexed { i, r -> (i + 1L) to r }.toMap())

    private var state by mutableStateOf(deck(landscape, portrait, landscape, portrait))

    private fun show(initial: SwipeState) {
        state = initial
        composeRule.setContent {
            MemoryLaneTheme { SwipeContent(state = state, onIntent = {}) }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(id: Long): DpRect = composeRule.onNodeWithTag(SwipeTags.cardPhoto(id)).getUnclippedBoundsInRoot()

    private fun ratio(r: DpRect) = r.width.value / r.height.value

    private fun dragTop(dx: Float) {
        composeRule.onNodeWithTag(SwipeTags.CARD).performTouchInput {
            down(center)
            moveBy(Offset(dx, 0f))
        }
        composeRule.waitForIdle()
    }

    // --- at rest ---

    @Test
    fun `at rest the card behind is 0_94 of the top card in both directions`() {
        show(deck(landscape, portrait))

        val top = bounds(1)
        val next = bounds(2)

        assertEquals(top.width.value * BEHIND_SCALE, next.width.value, tolerance)
        assertEquals(top.height.value * BEHIND_SCALE, next.height.value, tolerance)
    }

    @Test
    fun `at rest the card behind has the shape of the top card, not its own`() {
        show(deck(landscape, portrait))

        assertEquals(landscape, ratio(bounds(1)), 0.01f)
        assertEquals(landscape, ratio(bounds(2)), 0.01f)
    }

    @Test
    fun `at rest the card behind does not stick out at the sides or below the top card`() {
        show(deck(landscape, portrait))

        val top = bounds(1)
        val next = bounds(2)

        assertTrue("left", next.left.value >= top.left.value - tolerance)
        assertTrue("right", next.right.value <= top.right.value + tolerance)
        assertTrue("bottom", next.bottom.value <= top.bottom.value + tolerance)
    }

    @Test
    fun `at rest only a thin edge of the card behind shows above the top card`() {
        show(deck(landscape, portrait))

        val top = bounds(1)
        val next = bounds(2)

        assertEquals(BEHIND_PEEK, top.top.value - next.top.value, tolerance)
    }

    @Test
    fun `a tall top card has the same thin edge above it`() {
        show(deck(portrait, landscape))

        val top = bounds(1)
        val next = bounds(2)

        assertEquals(BEHIND_PEEK, top.top.value - next.top.value, tolerance)
        assertTrue(next.bottom.value <= top.bottom.value + tolerance)
    }

    @Test
    fun `a card of the same shape behind is the same scaled card with the edge above`() {
        show(deck(portrait, portrait))

        val top = bounds(1)
        val next = bounds(2)

        assertEquals(top.width.value * BEHIND_SCALE, next.width.value, tolerance)
        assertEquals(BEHIND_PEEK, top.top.value - next.top.value, tolerance)
    }

    @Test
    fun `the top card keeps its own shape and is never cropped`() {
        show(deck(landscape, portrait))

        assertEquals(landscape, ratio(bounds(1)), 0.01f)
    }

    // --- while the top card is dragged ---

    @Test
    fun `half way through the drag the card behind is between its two shapes`() {
        show(deck(landscape, portrait))
        val rest = bounds(2)

        // About half of what the library needs to take the card behind to its full size (the touch slop comes off first)
        dragTop(dx = 200f)
        val during = bounds(2)

        // Its own shape is a taller one, and wider here too: both grow from the hint
        assertTrue("taller than at rest ${during.height} ${rest.height}", during.height.value > rest.height.value + tolerance)
        assertTrue("wider than at rest", during.width.value > rest.width.value + 0.5f)
        assertTrue("not its own size yet", ratio(during) > portrait + 0.02f && ratio(during) < landscape - 0.02f)
    }

    @Test
    fun `dragged far enough the card behind has its own shape and size`() {
        show(deck(landscape, portrait))

        dragTop(dx = 600f)
        val next = bounds(2)

        assertEquals(portrait, ratio(next), 0.01f)
        // 344dp wide: the card a portrait photo has as the top card on this 360dp screen
        assertEquals(344f, next.width.value, 2f)
    }

    @Test
    fun `the card behind grows with the drag`() {
        show(deck(landscape, portrait))
        val heights = mutableListOf(bounds(2).height.value)

        composeRule.onNodeWithTag(SwipeTags.CARD).performTouchInput {
            down(center)
            listOf(20f, 20f, 20f, 20f, 20f).forEach { step ->
                moveBy(Offset(step, 0f))
            }
        }
        composeRule.waitForIdle()
        heights += bounds(2).height.value

        assertTrue("${heights}", heights.last() > heights.first())
    }

    @Test
    fun `the card behind is where the top card will be once the swipe is done`() {
        show(deck(landscape, portrait, landscape))
        dragTop(dx = 600f)
        val dragged = bounds(2)

        // The state moves on: card 2 is now the top card, centred at its own size
        state = state.copy(currentIndex = 1)
        composeRule.waitForIdle()
        val top = bounds(2)

        assertEquals(dragged.width.value, top.width.value, tolerance)
        assertEquals(dragged.height.value, top.height.value, tolerance)
        assertEquals(dragged.left.value, top.left.value, tolerance)
    }

    // --- undo and jumps ---

    @Test
    fun `after a jump the new top card has its own shape and the one behind it the hint`() {
        show(deck(landscape, portrait, landscape, portrait))

        state = state.copy(currentIndex = 2)
        composeRule.waitForIdle()
        val top = bounds(3)
        val next = bounds(4)

        assertEquals(landscape, ratio(top), 0.01f)
        assertEquals(landscape, ratio(next), 0.01f)
        assertEquals(top.width.value * BEHIND_SCALE, next.width.value, tolerance)
        assertEquals(BEHIND_PEEK, top.top.value - next.top.value, tolerance)
    }

    @Test
    fun `after an undo the returned card has its own shape and the one behind it the hint`() {
        show(deck(landscape, portrait, landscape).copy(currentIndex = 1))

        state = state.copy(currentIndex = 0)
        composeRule.waitForIdle()
        val top = bounds(1)
        val next = bounds(2)

        assertEquals(landscape, ratio(top), 0.01f)
        assertEquals(top.width.value * BEHIND_SCALE, next.width.value, tolerance)
        assertEquals(BEHIND_PEEK, top.top.value - next.top.value, tolerance)
    }

    @Test
    fun `a jump from a portrait to a landscape card leaves no card at a wrong size`() {
        show(deck(portrait, landscape, portrait))

        state = state.copy(currentIndex = 1)
        composeRule.waitForIdle()
        val top = bounds(2)
        val next = bounds(3)

        assertEquals(landscape, ratio(top), 0.01f)
        assertEquals(landscape, ratio(next), 0.01f)
        assertTrue(next.width.value <= top.width.value + tolerance)
        assertTrue(next.height.value <= top.height.value + tolerance)
    }
}
