package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import com.haroncode.lazycardstack.swiper.SwipeDirection
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeIntent
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class SwipeContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sent = mutableListOf<SwipeIntent>()

    private fun show(state: SwipeState) {
        composeRule.setContent {
            MemoryLaneTheme { SwipeContent(state = state, onIntent = { sent += it }) }
        }
    }

    private fun invokeCardAction(label: String) {
        val actions = composeRule.onNodeWithTag(SwipeTags.CARD).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        val action = actions.first { it.label == label }
        composeRule.runOnUiThread { action.action() }
    }

    private fun waitForIntent() {
        composeRule.waitUntil(timeoutMillis = 10_000) { sent.isNotEmpty() }
    }

    // --- what is rendered ---

    @Test
    fun `the date and the place of the current photo are shown`() {
        show(swipeState(count = 3, currentIndex = 1, location = "Светлогорск, Россия"))

        composeRule.onNodeWithTag(SwipeTags.TOP_BAR_DATE).assertTextEquals("7 января 2017, 21:15")
        composeRule.onNodeWithTag(SwipeTags.TOP_BAR_LOCATION).assertTextEquals("Светлогорск, Россия")
    }

    @Test
    fun `the card and the strip are shown`() {
        show(swipeState(count = 3))

        composeRule.onNodeWithTag(SwipeTags.CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(SwipeTags.STRIP).assertIsDisplayed()
        composeRule.onNodeWithTag(SwipeTags.stripItem(1)).assertIsDisplayed()
    }

    @Test
    fun `the strip holds every photo of the deck and the trashed ones`() {
        show(swipeState(count = 3, trashed = listOf(deckPhoto(10, PhotoDecision.TRASHED))))

        composeRule.onAllNodesWithTag(SwipeTags.STRIP).assertCountEquals(1)
        composeRule.onNodeWithTag(SwipeTags.stripItem(10)).assertExists()
        (1L..3L).forEach { composeRule.onNodeWithTag(SwipeTags.stripItem(it)).assertExists() }
    }

    @Test
    fun `the date is not shown for a photo that has none`() {
        show(swipeState(count = 0))

        composeRule.onAllNodesWithTag(SwipeTags.TOP_BAR_DATE).assertCountEquals(0)
    }

    @Test
    fun `no place leaves its row empty and the card where it was`() {
        var state by mutableStateOf(swipeState(count = 2, location = "Париж, Франция"))
        composeRule.setContent {
            MemoryLaneTheme { SwipeContent(state = state, onIntent = { sent += it }) }
        }
        val withPlace = composeRule.onNodeWithTag(SwipeTags.CARD).getUnclippedBoundsInRoot()

        composeRule.runOnIdle { state = swipeState(count = 2, location = null) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(SwipeTags.TOP_BAR_LOCATION).assertCountEquals(0)
        val withoutPlace = composeRule.onNodeWithTag(SwipeTags.CARD).getUnclippedBoundsInRoot()
        assertEquals(withPlace.top, withoutPlace.top)
        assertEquals(withPlace.height, withoutPlace.height)
    }

    @Test
    fun `a finished day shows no card`() {
        show(swipeState(count = 2, currentIndex = 2).copy(isFinished = true))

        composeRule.onAllNodesWithTag(SwipeTags.CARD).assertCountEquals(0)
    }

    @Test
    fun `a completed day opens on its first photo with the trashed ones in the strip only`() {
        val decided = mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE)
        show(swipeState(count = 2, decisions = decided, trashed = listOf(deckPhoto(10, PhotoDecision.TRASHED))))

        composeRule.onNodeWithTag(SwipeTags.TOP_BAR_DATE).assertTextEquals("7 января 2016, 21:15")
        composeRule.onNodeWithTag(SwipeTags.stripItem(10)).assertExists()
        composeRule.onNodeWithTag(SwipeTags.CARD).assertIsDisplayed()
    }

    @Test
    fun `the strip items describe their photo and its state`() {
        show(swipeState(count = 2, decisions = mapOf(1L to PhotoDecision.KEEP)))

        composeRule.onNodeWithContentDescription("Фото 1 из 2, оставлено").assertExists()
        composeRule.onNodeWithContentDescription("Фото 2 из 2, не разобрано").assertExists()
    }

    // --- accessibility actions on the card ---

    @Test
    fun `the card offers Удалить and Оставить as accessibility actions`() {
        show(swipeState(count = 2))

        val labels = composeRule.onNodeWithTag(SwipeTags.CARD).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Удалить", "Оставить"), labels)
    }

    @Test
    fun `the Удалить action sends a left swipe, with buttons mode off`() {
        show(swipeState(count = 2, buttonsMode = false))

        invokeCardAction("Удалить")
        waitForIntent()

        assertEquals(listOf<SwipeIntent>(SwipeIntent.Swipe(SwipeDirection.Left)), sent)
    }

    @Test
    fun `the Оставить action sends a right swipe, with buttons mode off`() {
        show(swipeState(count = 2, buttonsMode = false))

        invokeCardAction("Оставить")
        waitForIntent()

        assertEquals(listOf<SwipeIntent>(SwipeIntent.Swipe(SwipeDirection.Right)), sent)
    }

    // --- buttons mode ---

    @Test
    fun `the buttons are not shown while buttons mode is off`() {
        show(swipeState(count = 2, buttonsMode = false))

        composeRule.onAllNodesWithTag(SwipeTags.BUTTON_DELETE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SwipeTags.BUTTON_KEEP).assertCountEquals(0)
    }

    @Test
    fun `in buttons mode the cross sends a left swipe`() {
        show(swipeState(count = 2, buttonsMode = true))

        composeRule.onNodeWithTag(SwipeTags.BUTTON_DELETE).performClick()
        waitForIntent()

        assertEquals(listOf<SwipeIntent>(SwipeIntent.Swipe(SwipeDirection.Left)), sent)
    }

    @Test
    fun `in buttons mode the tick sends a right swipe`() {
        show(swipeState(count = 2, buttonsMode = true))

        composeRule.onNodeWithTag(SwipeTags.BUTTON_KEEP).performClick()
        waitForIntent()

        assertEquals(listOf<SwipeIntent>(SwipeIntent.Swipe(SwipeDirection.Right)), sent)
    }

    @Test
    fun `the buttons have content descriptions`() {
        show(swipeState(count = 2, buttonsMode = true))

        composeRule.onNodeWithContentDescription("Удалить фото").assertExists()
        composeRule.onNodeWithContentDescription("Оставить фото").assertExists()
    }

    // --- the strip ---

    @Test
    fun `a tap on a strip photo sends a jump to its deck index`() {
        show(swipeState(count = 3))

        composeRule.onNodeWithTag(SwipeTags.stripItem(3)).performClick()

        assertEquals(listOf<SwipeIntent>(SwipeIntent.JumpTo(2)), sent)
    }

    @Test
    fun `a tap on a trashed photo sends nothing`() {
        show(swipeState(count = 2, trashed = listOf(deckPhoto(10, PhotoDecision.TRASHED))))

        composeRule.onNodeWithTag(SwipeTags.stripItem(10)).performClick()

        assertTrue(sent.isEmpty())
    }

    // --- quick actions ---

    @Test
    fun `undo is disabled while there is nothing to undo`() {
        show(swipeState(count = 2))

        composeRule.onNodeWithTag(SwipeTags.ACTION_UNDO).assertIsNotEnabled()
    }

    @Test
    fun `a tap on a disabled undo sends nothing`() {
        show(swipeState(count = 2))

        composeRule.onNodeWithTag(SwipeTags.ACTION_UNDO).performClick()

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `undo is enabled and sends Undo once there is something to undo`() {
        show(swipeState(count = 2).copy(undoStack = persistentListOf(com.kllxx.memorylane.feature.swipe.impl.swipe.UndoEntry(1, null))))

        composeRule.onNodeWithTag(SwipeTags.ACTION_UNDO).assertIsEnabled().performClick()

        assertEquals(listOf<SwipeIntent>(SwipeIntent.Undo), sent)
    }

    @Test
    fun `share and open in the gallery send their intents`() {
        show(swipeState(count = 2))

        composeRule.onNodeWithTag(SwipeTags.ACTION_SHARE).performClick()
        composeRule.onNodeWithTag(SwipeTags.ACTION_GALLERY).performClick()

        assertEquals(listOf<SwipeIntent>(SwipeIntent.Share, SwipeIntent.OpenInGallery), sent)
    }

    @Test
    fun `the buttons switch sends a toggle and is on only in buttons mode`() {
        show(swipeState(count = 2, buttonsMode = false))

        composeRule.onNodeWithTag(SwipeTags.ACTION_BUTTONS).assertIsOff().performClick()

        assertEquals(listOf<SwipeIntent>(SwipeIntent.ToggleButtonsMode), sent)
    }

    @Test
    fun `the buttons switch is on in buttons mode`() {
        show(swipeState(count = 2, buttonsMode = true))

        composeRule.onNodeWithTag(SwipeTags.ACTION_BUTTONS).assertIsOn()
    }

    @Test
    fun `the buttons switch follows the state when it changes`() {
        var state by mutableStateOf(swipeState(count = 2, buttonsMode = false))
        composeRule.setContent {
            MemoryLaneTheme {
                SwipeContent(state = state, onIntent = { intent ->
                    sent += intent
                    if (intent == SwipeIntent.ToggleButtonsMode) state = state.copy(buttonsMode = !state.buttonsMode)
                })
            }
        }

        composeRule.onNodeWithTag(SwipeTags.ACTION_BUTTONS).performClick()
        composeRule.onNodeWithTag(SwipeTags.ACTION_BUTTONS).assertIsOn()
        composeRule.onNodeWithTag(SwipeTags.BUTTON_DELETE).assertExists()

        composeRule.onNodeWithTag(SwipeTags.ACTION_BUTTONS).performClick()
        composeRule.onNodeWithTag(SwipeTags.ACTION_BUTTONS).assertIsOff()
        composeRule.onAllNodesWithTag(SwipeTags.BUTTON_DELETE).assertCountEquals(0)
    }

    @Test
    fun `every quick action has a content description`() {
        show(swipeState(count = 2))

        composeRule.onNodeWithContentDescription("Отменить последнее решение").assertExists()
        composeRule.onNodeWithContentDescription("Поделиться фото").assertExists()
        composeRule.onNodeWithContentDescription("Открыть фото в галерее").assertExists()
        composeRule.onNodeWithContentDescription("Кнопки «Удалить» и «Оставить» на карточке").assertExists()
        composeRule.onNodeWithContentDescription("Закрыть").assertExists()
    }

    @Test
    fun `close sends Close`() {
        show(swipeState(count = 2))

        composeRule.onNodeWithTag(SwipeTags.CLOSE).performClick()

        assertEquals(listOf<SwipeIntent>(SwipeIntent.Close), sent)
    }
}
