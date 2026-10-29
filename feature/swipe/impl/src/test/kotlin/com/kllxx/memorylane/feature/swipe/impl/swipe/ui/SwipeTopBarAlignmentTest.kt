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
 * The visible text of the top bar (the date alone, or the date and the place) is centred on the close button, and the
 * bar is as high for a photo without a place as for one with a place, so the card below does not move.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class SwipeTopBarAlignmentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tolerance = 0.5f
    private val place = "Светлогорск, Россия"

    private fun show(location: String?, fontScale: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MemoryLaneTheme {
                    SwipeTopBar(dateText = "7 января 2016, 21:15", location = location, onClose = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun centre(rect: DpRect) = (rect.top.value + rect.bottom.value) / 2

    private fun height(rect: DpRect) = rect.bottom.value - rect.top.value

    private fun assertCentred(location: String?, fontScale: Float) {
        show(location, fontScale)

        val close = bounds(SwipeTags.CLOSE)
        assertEquals(40f, height(close), tolerance)
        // The date alone, or the date and the place: the text that is on the screen
        val visible = if (location == null) bounds(SwipeTags.TOP_BAR_DATE) else bounds(SwipeTags.TOP_BAR_TEXT)
        assertEquals(centre(close), centre(visible), tolerance)
    }

    @Test
    fun `with a place the date and the place are centred on the close button`() = assertCentred(place, 1f)

    @Test
    fun `without a place the date alone is centred on the close button`() = assertCentred(null, 1f)

    @Test
    fun `with a place the date and the place stay centred at a font scale of 2`() = assertCentred(place, 2f)

    @Test
    fun `without a place the date alone stays centred at a font scale of 2`() = assertCentred(null, 2f)

    @Test
    fun `without a place the text column is only the date and there is no place row`() {
        show(location = null, fontScale = 1f)

        composeRule.onNodeWithTag(SwipeTags.TOP_BAR_LOCATION).assertDoesNotExist()
        assertEquals(height(bounds(SwipeTags.TOP_BAR_DATE)), height(bounds(SwipeTags.TOP_BAR_TEXT)), tolerance)
    }

    // --- the card below does not move ---

    private fun cardTopWithAndWithoutPlace(fontScale: Float): Pair<Float, Float> {
        var state by mutableStateOf(swipeState(count = 3, aspectRatio = 0.75f, location = place))
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MemoryLaneTheme { SwipeContent(state = state as SwipeState, onIntent = {}) }
            }
        }
        composeRule.waitForIdle()
        val withPlace = bounds(SwipeTags.CARD).top.value
        val barWithPlace = height(bounds(SwipeTags.CLOSE))

        composeRule.runOnIdle { state = swipeState(count = 3, aspectRatio = 0.75f, location = null) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SwipeTags.TOP_BAR_LOCATION).assertDoesNotExist()
        assertTrue(barWithPlace > 0f)
        return withPlace to bounds(SwipeTags.CARD).top.value
    }

    @Test
    fun `the card does not move between a photo with and without a place`() {
        val (withPlace, withoutPlace) = cardTopWithAndWithoutPlace(fontScale = 1f)

        assertEquals(withPlace, withoutPlace, tolerance)
    }

    @Test
    fun `the card does not move between a photo with and without a place at a font scale of 2`() {
        val (withPlace, withoutPlace) = cardTopWithAndWithoutPlace(fontScale = 2f)

        assertEquals(withPlace, withoutPlace, tolerance)
    }
}
