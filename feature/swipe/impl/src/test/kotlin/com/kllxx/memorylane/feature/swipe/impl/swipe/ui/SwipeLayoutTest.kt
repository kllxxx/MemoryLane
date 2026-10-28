package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The layout of the Swipe screen: the card has the shape of its photo and is centred in the card area, the buttons
 * sit on its bottom edge, and the bottom panel is where the design puts it. The screen is 360 by 800dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SwipeLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val portrait = 3f / 4f
    private val landscape = 4f / 3f
    private val tolerance = 1f

    private fun show(state: SwipeState) {
        composeRule.setContent {
            MemoryLaneTheme { SwipeContent(state = state, onIntent = {}) }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private val DpRect.centerX get() = (left.value + right.value) / 2f
    private val DpRect.centerY get() = (top.value + bottom.value) / 2f

    /** The card area: the stack container less the inset on each side and the room above and below. */
    private fun cardArea(): DpRect {
        val container = bounds(SwipeTags.CARD)
        return DpRect(
            left = container.left + SwipeLayout.CardInset,
            top = container.top + SwipeLayout.CardAreaTop,
            right = container.right - SwipeLayout.CardInset,
            bottom = container.bottom - SwipeLayout.CardAreaBottom,
        )
    }

    // --- the card has the shape of its photo ---

    @Test
    fun `a portrait photo is a tall card`() {
        show(swipeState(count = 2, aspectRatio = portrait))

        val card = bounds(SwipeTags.cardPhoto(1))

        assertTrue("taller than wide", card.height.value > card.width.value)
        assertEquals(portrait, card.width.value / card.height.value, 0.01f)
    }

    @Test
    fun `a landscape photo is a wide, short card`() {
        show(swipeState(count = 2, aspectRatio = landscape))

        val card = bounds(SwipeTags.cardPhoto(1))

        assertTrue("wider than tall", card.width.value > card.height.value)
        assertEquals(landscape, card.width.value / card.height.value, 0.01f)
    }

    @Test
    fun `a landscape card is as wide as the card area`() {
        show(swipeState(count = 2, aspectRatio = landscape))

        assertEquals(cardArea().width.value, bounds(SwipeTags.cardPhoto(1)).width.value, tolerance)
    }

    @Test
    fun `a very tall photo is as high as the card area`() {
        show(swipeState(count = 2, aspectRatio = 1f / 3f))

        assertEquals(cardArea().height.value, bounds(SwipeTags.cardPhoto(1)).height.value, tolerance)
    }

    @Test
    fun `a landscape card is centred in the card area`() {
        show(swipeState(count = 2, aspectRatio = landscape))

        val card = bounds(SwipeTags.cardPhoto(1))
        val area = cardArea()

        assertEquals(area.centerX, card.centerX, tolerance)
        assertEquals(area.centerY, card.centerY, tolerance)
    }

    @Test
    fun `a portrait card is centred in the card area`() {
        show(swipeState(count = 2, aspectRatio = portrait))

        val card = bounds(SwipeTags.cardPhoto(1))
        val area = cardArea()

        assertEquals(area.centerX, card.centerX, tolerance)
        assertEquals(area.centerY, card.centerY, tolerance)
    }

    @Test
    fun `the card stays inside the card area`() {
        show(swipeState(count = 2, aspectRatio = portrait))

        val card = bounds(SwipeTags.cardPhoto(1))
        val area = cardArea()

        assertTrue(card.left.value >= area.left.value - tolerance)
        assertTrue(card.right.value <= area.right.value + tolerance)
        assertTrue(card.top.value >= area.top.value - tolerance)
        assertTrue(card.bottom.value <= area.bottom.value + tolerance)
    }

    @Test
    fun `a photo whose size is not known is a portrait card until it is decoded`() {
        show(swipeState(count = 2, aspectRatio = null))

        val card = bounds(SwipeTags.cardPhoto(1))

        assertEquals(0.75f, card.width.value / card.height.value, 0.01f)
    }

    @Test
    fun `the card is not cropped, it has the ratio of the photo`() {
        show(swipeState(count = 2, aspectRatio = 16f / 9f))

        val card = bounds(SwipeTags.cardPhoto(1))

        assertEquals(16f / 9f, card.width.value / card.height.value, 0.01f)
    }

    // --- the buttons sit on the bottom edge of the card ---

    @Test
    fun `the buttons are centred on the bottom edge of a portrait card`() {
        show(swipeState(count = 2, aspectRatio = portrait, buttonsMode = true))

        val card = bounds(SwipeTags.cardPhoto(1))
        val delete = bounds(SwipeTags.BUTTON_DELETE)
        val keep = bounds(SwipeTags.BUTTON_KEEP)

        assertEquals(card.bottom.value, delete.centerY, tolerance)
        assertEquals(card.bottom.value, keep.centerY, tolerance)
    }

    @Test
    fun `the buttons are centred on the bottom edge of a landscape card`() {
        show(swipeState(count = 2, aspectRatio = landscape, buttonsMode = true))

        val card = bounds(SwipeTags.cardPhoto(1))
        val delete = bounds(SwipeTags.BUTTON_DELETE)
        val keep = bounds(SwipeTags.BUTTON_KEEP)

        assertEquals(card.bottom.value, delete.centerY, tolerance)
        assertEquals(card.bottom.value, keep.centerY, tolerance)
    }

    @Test
    fun `the buttons follow the width of the card`() {
        show(swipeState(count = 2, aspectRatio = 1f / 3f, buttonsMode = true))

        val card = bounds(SwipeTags.cardPhoto(1))
        val delete = bounds(SwipeTags.BUTTON_DELETE)
        val keep = bounds(SwipeTags.BUTTON_KEEP)

        assertEquals(card.left.value + card.width.value * 0.2875f, delete.centerX, tolerance)
        assertEquals(card.left.value + card.width.value * 0.7125f, keep.centerX, tolerance)
    }

    @Test
    fun `the cross is left of the tick, and both are 60dp`() {
        show(swipeState(count = 2, aspectRatio = portrait, buttonsMode = true))

        val delete = bounds(SwipeTags.BUTTON_DELETE)
        val keep = bounds(SwipeTags.BUTTON_KEEP)

        assertTrue(delete.centerX < keep.centerX)
        assertEquals(60f, delete.width.value, tolerance)
        assertEquals(60f, keep.height.value, tolerance)
    }

    @Test
    fun `the buttons stay inside the stack area, so all of each circle can be tapped`() {
        show(swipeState(count = 2, aspectRatio = 1f / 3f, buttonsMode = true))

        val container = bounds(SwipeTags.CARD)
        val delete = bounds(SwipeTags.BUTTON_DELETE)

        assertTrue(delete.bottom.value <= container.bottom.value + tolerance)
    }

    // --- the photo is as large as possible ---

    @Test
    fun `a 3 by 4 portrait photo is 344dp wide on a 360dp screen`() {
        show(swipeState(count = 2, aspectRatio = portrait))

        val card = bounds(SwipeTags.cardPhoto(1))

        assertEquals(344f, card.width.value, tolerance)
        assertEquals(344f / portrait, card.height.value, tolerance)
    }

    @Test
    fun `the top bar is 56dp high`() {
        show(swipeState(count = 2, aspectRatio = portrait, location = "Светлогорск, Россия"))

        val container = bounds(SwipeTags.CARD)

        // 2dp above and below the 56dp top bar
        assertEquals(2f + 56f + 2f, container.top.value, tolerance)
    }

    // --- the actions card ---

    @Test
    fun `the actions card is 12dp from the screen edges and from the bottom`() {
        show(swipeState(count = 2, aspectRatio = portrait))

        val card = bounds(SwipeTags.ACTIONS_CARD)

        assertEquals(12f, card.left.value, tolerance)
        assertEquals(360f - 12f, card.right.value, tolerance)
        assertEquals(800f - 12f, card.bottom.value, tolerance)
    }

    @Test
    fun `the actions card holds only the four actions`() {
        show(swipeState(count = 3, aspectRatio = portrait))

        val card = bounds(SwipeTags.ACTIONS_CARD)
        listOf(SwipeTags.ACTION_UNDO, SwipeTags.ACTION_SHARE, SwipeTags.ACTION_GALLERY, SwipeTags.ACTION_BUTTONS)
            .forEach { tag ->
                val node = bounds(tag)
                assertTrue("$tag inside left", node.left.value >= card.left.value - tolerance)
                assertTrue("$tag inside right", node.right.value <= card.right.value + tolerance)
                assertTrue("$tag inside top", node.top.value >= card.top.value - tolerance)
                assertTrue("$tag inside bottom", node.bottom.value <= card.bottom.value + tolerance)
            }
    }

    @Test
    fun `the strip floats outside the actions card, 8dp above it`() {
        show(swipeState(count = 3, aspectRatio = portrait))

        val strip = bounds(SwipeTags.STRIP)
        val card = bounds(SwipeTags.ACTIONS_CARD)

        assertEquals(card.top.value - 8f, strip.bottom.value, tolerance)
        assertEquals(0f, strip.left.value, tolerance)
        assertEquals(360f, strip.right.value, tolerance)
    }

    @Test
    fun `the strip is right under the photo area`() {
        show(swipeState(count = 3, aspectRatio = portrait))

        // The card area ends 8dp above the strip
        assertEquals(bounds(SwipeTags.STRIP).top.value, bounds(SwipeTags.CARD).bottom.value, tolerance)
    }

    @Test
    fun `the four actions are in equal columns across the panel`() {
        show(swipeState(count = 3, aspectRatio = portrait))

        val centers = listOf(SwipeTags.ACTION_UNDO, SwipeTags.ACTION_SHARE, SwipeTags.ACTION_GALLERY, SwipeTags.ACTION_BUTTONS)
            .map { bounds(it).centerX }
        val steps = centers.zipWithNext { a, b -> b - a }

        // The card is 336dp wide and the row has 8dp on each side, so the columns are 80dp apart
        steps.forEach { assertEquals(80f, it, tolerance) }
    }

    @Test
    fun `the card area ends above the strip`() {
        show(swipeState(count = 2, aspectRatio = 1f / 3f))

        assertTrue(bounds(SwipeTags.cardPhoto(1)).bottom.value <= bounds(SwipeTags.STRIP).top.value)
    }

    @Test
    fun `in buttons mode the circles of a tall card stay above the strip`() {
        show(swipeState(count = 2, aspectRatio = 1f / 3f, buttonsMode = true))

        assertTrue(bounds(SwipeTags.BUTTON_DELETE).bottom.value <= bounds(SwipeTags.STRIP).top.value + tolerance)
    }

    // --- the strip ---

    @Test
    fun `strip thumbnails are 28dp wide and the current one 34dp`() {
        show(swipeState(count = 4, currentIndex = 1, decisions = mapOf(1L to PhotoDecision.KEEP), aspectRatio = portrait))

        assertEquals(28f, bounds(SwipeTags.stripItem(1)).width.value, tolerance)
        assertEquals(34f, bounds(SwipeTags.stripItem(2)).width.value, tolerance)
        assertEquals(28f, bounds(SwipeTags.stripItem(3)).width.value, tolerance)
    }

    @Test
    fun `the current thumbnail is centred in the strip`() {
        show(swipeState(count = 7, currentIndex = 3, aspectRatio = portrait))

        val strip = bounds(SwipeTags.STRIP)
        val current = bounds(SwipeTags.stripItem(4))

        assertEquals(strip.centerX, current.centerX, tolerance * 2)
    }

    @Test
    fun `the dots line up under every thumbnail whatever its size`() {
        show(swipeState(count = 3, currentIndex = 1, aspectRatio = portrait))

        // Every item is as high as the largest thumbnail, a gap and a dot, so the rows of thumbnails and dots align
        val heights = (1L..3L).map { bounds(SwipeTags.stripItem(it)).height.value }

        assertEquals(1, heights.distinct().size)
    }
}
