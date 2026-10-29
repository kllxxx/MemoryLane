package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The text block of the top bar and the close button have the same vertical centre, with or without a place. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class SwipeTopBarAlignmentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tolerance = 0.5f

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

    private fun assertCentred(location: String?, fontScale: Float) {
        show(location, fontScale)

        val text = bounds(SwipeTags.TOP_BAR_TEXT)
        val close = bounds(SwipeTags.CLOSE)

        assertEquals(40f, close.bottom.value - close.top.value, tolerance)
        assertEquals(centre(close), centre(text), tolerance)
    }

    @Test
    fun `with a place the text block is centred on the close button`() = assertCentred("Светлогорск, Россия", 1f)

    @Test
    fun `without a place the text block is still centred on the close button`() = assertCentred(null, 1f)

    @Test
    fun `with a place the text block stays centred at a font scale of 2`() = assertCentred("Светлогорск, Россия", 2f)

    @Test
    fun `without a place the text block stays centred at a font scale of 2`() = assertCentred(null, 2f)
}
