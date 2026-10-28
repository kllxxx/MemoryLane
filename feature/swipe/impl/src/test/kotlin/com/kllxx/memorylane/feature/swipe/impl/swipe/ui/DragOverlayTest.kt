package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tint and the badge on the card while it is dragged are decoration: a screen reader must not announce them. A
 * screen reader reads the merged semantics tree, which is what these tests look at.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class DragOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun dragAndHold(dx: Float) {
        composeRule.setContent {
            MemoryLaneTheme { SwipeContent(state = swipeState(count = 3, aspectRatio = 0.75f), onIntent = {}) }
        }
        composeRule.waitForIdle()
        val before = composeRule.onNodeWithTag(SwipeTags.cardPhoto(1)).getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag(SwipeTags.CARD).performTouchInput {
            down(center)
            moveBy(Offset(dx, 0f))
        }
        composeRule.waitForIdle()
        val after = composeRule.onNodeWithTag(SwipeTags.cardPhoto(1)).getUnclippedBoundsInRoot()
        // The drag really moved the card, so the overlay is being drawn
        assertTrue("the card moved", after.left != before.left)
    }

    @Test
    fun `the delete badge is not in the accessibility tree while dragging left`() {
        dragAndHold(-160f)

        composeRule.onAllNodesWithText("УДАЛИТЬ").assertCountEquals(0)
    }

    @Test
    fun `the keep badge is not in the accessibility tree while dragging right`() {
        dragAndHold(160f)

        composeRule.onAllNodesWithText("ОСТАВИТЬ").assertCountEquals(0)
    }

    @Test
    fun `no badge text is announced at rest either`() {
        composeRule.setContent {
            MemoryLaneTheme { SwipeContent(state = swipeState(count = 3, aspectRatio = 0.75f), onIntent = {}) }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("УДАЛИТЬ").assertCountEquals(0)
        composeRule.onAllNodesWithText("ОСТАВИТЬ").assertCountEquals(0)
    }
}
