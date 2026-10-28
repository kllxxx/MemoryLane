package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The labels under the quick actions are shown in full: one line that is not cut, whose box is not near the rounded
 * corners of whatever holds it, at the normal font and at a large one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class QuickActionsLabelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = listOf(
        SwipeTags.ACTION_UNDO,
        SwipeTags.ACTION_SHARE,
        SwipeTags.ACTION_GALLERY,
        SwipeTags.ACTION_BUTTONS,
    )

    private fun show(fontScale: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MemoryLaneTheme {
                    QuickActionsCard(
                        canUndo = true,
                        buttonsMode = false,
                        onUndo = {},
                        onShare = {},
                        onOpenInGallery = {},
                        onToggleButtons = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun layoutOf(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = composeRule.onNodeWithTag(label, useUnmergedTree = true).fetchSemanticsNode()
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertNotNull("no text layout for $label", action)
        assertTrue(action!!.invoke(results))
        return results.first()
    }

    private fun assertLabelsAreWhole(fontScale: Float, mayEllipsize: Boolean = false) {
        show(fontScale)
        actions.forEach { action ->
            val layout = layoutOf(SwipeTags.actionLabel(action))
            assertEquals("$action is one line", 1, layout.lineCount)
            // hasVisualOverflow is not used: an ellipsizing text lays out at its maximum width and is then as wide as its
            // line, so Compose reports an overflow for any label that fits
            assertTrue("$action line is wider than its box", layout.getLineRight(0) <= layout.size.width + 1f)
            assertTrue("$action line is higher than its box", layout.getLineBottom(0) <= layout.size.height + 1f)
            if (!mayEllipsize) assertFalse("$action exceeds its lines", layout.multiParagraph.didExceedMaxLines)
            if (!mayEllipsize) assertFalse("$action is cut with an ellipsis", layout.isLineEllipsized(0))
        }
    }

    @Test
    fun `the labels are one whole line at the normal font`() = assertLabelsAreWhole(fontScale = 1f)

    @Test
    // The font is capped at 1.3x there, and a long label may then really not fit: only then does it get an ellipsis
    fun `the labels are one line, with the box around the text, at a large font`() = assertLabelsAreWhole(fontScale = 2f, mayEllipsize = true)

    @Test
    fun `the label box is inside its action and clear of the action's rounded corners`() {
        show(fontScale = 1f)

        actions.forEach { action ->
            val holder = bounds(action)
            val label = composeRule.onNodeWithTag(SwipeTags.actionLabel(action), useUnmergedTree = true)
                .getUnclippedBoundsInRoot()

            assertTrue("$action label inside left", label.left.value >= holder.left.value + 4f - 0.5f)
            assertTrue("$action label inside right", label.right.value <= holder.right.value - 4f + 0.5f)
            assertTrue("$action label inside bottom", label.bottom.value <= holder.bottom.value - 2f + 0.5f)
        }
    }

    @Test
    fun `the label is at least as high as its line`() {
        show(fontScale = 1f)

        actions.forEach { action ->
            val label = composeRule.onNodeWithTag(SwipeTags.actionLabel(action), useUnmergedTree = true)
                .getUnclippedBoundsInRoot()

            // The line height is 16sp at the normal size
            assertTrue("$action label ${label.bottom.value - label.top.value}", label.bottom.value - label.top.value >= 16f - 0.5f)
        }
    }
}
