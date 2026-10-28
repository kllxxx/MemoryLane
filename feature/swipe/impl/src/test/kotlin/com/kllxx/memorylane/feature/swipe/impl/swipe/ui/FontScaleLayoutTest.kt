package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The screen at large font sizes and on other screens: the card area is whatever is left, and the text is not clipped. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class FontScaleLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tolerance = 1.5f

    private fun show(state: SwipeState, fontScale: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MemoryLaneTheme { SwipeContent(state = state, onIntent = {}) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun cardArea(): DpRect {
        val container = bounds(SwipeTags.CARD)
        return DpRect(
            left = container.left + SwipeLayout.CardInset,
            top = container.top + SwipeLayout.CardAreaTop,
            right = container.right - SwipeLayout.CardInset,
            bottom = container.bottom - SwipeLayout.CardAreaBottom,
        )
    }

    private val state = swipeState(count = 4, aspectRatio = 0.75f, location = "Светлогорск, Россия")

    // --- the card area is what is left, not a fixed size ---

    @Test
    fun `the card area is the screen minus the top bar, the strip and the actions card`() {
        show(state, fontScale = 1f)

        val top = bounds(SwipeTags.CARD).top.value
        val bottom = bounds(SwipeTags.STRIP).top.value
        val area = cardArea()

        // The photo area is between the top bar and the strip, with the 8dp gap above the strip inside it
        assertEquals(bottom - top, area.height.value + SwipeLayout.CardAreaTop.value + SwipeLayout.CardAreaBottom.value, tolerance)
        assertEquals(360f - 16f, area.width.value, tolerance)
    }

    @Test
    fun `on a 640dp screen the card area is 412dp high`() {
        show(state, fontScale = 1f)

        // 640 less the 2dp above and below the top bar, the 56dp top bar, the 52dp strip, the 8dp under it, the 84dp
        // actions card and its 12dp margin, and the 4dp and 8dp of room above and below the cards
        assertEquals(412f, cardArea().height.value, tolerance)
        assertEquals(344f, cardArea().width.value, tolerance)
    }

    @Test
    fun `at a large font the card still fits in the area and the area is on the screen`() {
        show(state, fontScale = 2f)

        val area = cardArea()
        val card = bounds(SwipeTags.cardPhoto(1))

        assertTrue(area.height.value > 100f)
        assertTrue(card.height.value <= area.height.value + tolerance)
        assertTrue(card.width.value <= area.width.value + tolerance)
        assertTrue(card.bottom.value <= bounds(SwipeTags.STRIP).top.value)
    }

    // --- the top bar does not clip its text at a large font ---

    @Test
    fun `the date and the place are not clipped at a large font, and the card starts below them`() {
        show(state, fontScale = 2f)

        val date = bounds(SwipeTags.TOP_BAR_DATE)
        val place = bounds(SwipeTags.TOP_BAR_LOCATION)
        val card = bounds(SwipeTags.cardPhoto(1))

        // 18sp at 2x is 36dp of text; its box has to be at least as high as the text
        assertTrue("date box ${date.height.value}", date.height.value >= 36f - tolerance)
        assertTrue("place box ${place.height.value}", place.height.value >= 26f - tolerance)
        assertTrue("date above place", date.bottom.value <= place.top.value + tolerance)
        assertTrue("card below the place", card.top.value >= place.bottom.value)
    }

    @Test
    fun `the top bar is taller at a large font than at the normal one`() {
        show(state, fontScale = 2f)
        val large = bounds(SwipeTags.TOP_BAR_LOCATION).bottom.value

        assertTrue(large > 56f)
    }

    @Test
    fun `the card does not jump between a photo with a place and one without, at a large font`() {
        var current by mutableStateOf(state)
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                MemoryLaneTheme { SwipeContent(state = current, onIntent = {}) }
            }
        }
        composeRule.waitForIdle()
        val withPlace = bounds(SwipeTags.cardPhoto(1))

        composeRule.runOnIdle { current = swipeState(count = 4, aspectRatio = 0.75f, location = null) }
        composeRule.waitForIdle()
        val withoutPlace = bounds(SwipeTags.cardPhoto(1))

        assertEquals(withPlace.top.value, withoutPlace.top.value, 0.5f)
        assertEquals(withPlace.height.value, withoutPlace.height.value, 0.5f)
    }

    // --- the labels of the actions ---

    @Test
    fun `the actions card keeps its height at a large font, because the labels stop growing`() {
        show(state, fontScale = 2f)

        // 84dp at the normal size; the labels grow to 1.3x at most, which is a few dp of line height
        assertEquals(84f, bounds(SwipeTags.ACTIONS_CARD).height.value, 8f)
    }

    @Test
    fun `the actions card is 84dp at the normal font`() {
        show(state, fontScale = 1f)

        assertEquals(84f, bounds(SwipeTags.ACTIONS_CARD).height.value, tolerance)
    }

    @Test
    fun `the four actions stay in equal columns at a large font`() {
        show(state, fontScale = 2f)

        val centers = listOf(SwipeTags.ACTION_UNDO, SwipeTags.ACTION_SHARE, SwipeTags.ACTION_GALLERY, SwipeTags.ACTION_BUTTONS)
            .map { (bounds(it).left.value + bounds(it).right.value) / 2f }

        centers.zipWithNext { a, b -> b - a }.forEach { assertEquals(80f, it, 6f) }
    }
}
