package com.kllxx.memorylane.feature.swipe.impl.beforedelete.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.width
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.BeforeDeleteIntent
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.BeforeDeleteReducer
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.BeforeDeleteState
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.DeleteCell
import com.kllxx.memorylane.feature.swipe.impl.swipe.ui.uriOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-rRU-w360dp-h800dp-xxhdpi")
class BeforeDeleteContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun cells(vararg sizes: Long) =
        sizes.mapIndexed { index, size -> DeleteCell(index + 1L, uriOf(index + 1L), size, marked = true) }

    private fun initial(vararg sizes: Long, deleting: Boolean = false, dialog: Boolean = false) = BeforeDeleteState(
        isLoading = false,
        cells = cells(*sizes).toImmutableList(),
        isDeleting = deleting,
        showPermanentDialog = dialog,
    )

    private val intents = mutableListOf<BeforeDeleteIntent>()
    private var backs = 0

    /** Shows the screen with the real reducer behind it, so a tap changes what is drawn like it does in the app. */
    private fun show(start: BeforeDeleteState) {
        composeRule.setContent {
            var state by remember { mutableStateOf(start) }
            MemoryLaneTheme {
                BeforeDeleteContent(
                    state = state,
                    onIntent = {
                        intents += it
                        state = BeforeDeleteReducer.reduce(state, it)
                    },
                    onBack = { backs++ },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun button() = composeRule.onNodeWithTag(BeforeDeleteTags.BUTTON)

    // --- the grid ---

    @Test
    fun `every photo of the grid is shown as marked`() {
        show(initial(150_000_000, 250_000_000, 10_000_000))

        (1L..3L).forEach { composeRule.onNodeWithTag(BeforeDeleteTags.cell(it)).assertIsOn() }
    }

    @Test
    fun `a tap unmarks a photo and a second tap marks it again`() {
        show(initial(150_000_000, 250_000_000))

        composeRule.onNodeWithTag(BeforeDeleteTags.cell(1)).performClick()
        composeRule.onNodeWithTag(BeforeDeleteTags.cell(1)).assertIsOff()
        composeRule.onNodeWithTag(BeforeDeleteTags.cell(2)).assertIsOn()

        composeRule.onNodeWithTag(BeforeDeleteTags.cell(1)).performClick()
        composeRule.onNodeWithTag(BeforeDeleteTags.cell(1)).assertIsOn()
        assertEquals(listOf(BeforeDeleteIntent.Toggle(1), BeforeDeleteIntent.Toggle(1)), intents)
    }

    @Test
    fun `three photos fit in a row and a 360dp screen gives 104dp cells`() {
        show(initial(1, 2, 3, 4))

        val widths = (1L..4L).map { composeRule.onNodeWithTag(BeforeDeleteTags.cell(it)).getUnclippedBoundsInRoot().width.value }

        widths.forEach { assertEquals(104f, it, 1f) }
        val firstRowRight = composeRule.onNodeWithTag(BeforeDeleteTags.cell(3)).getUnclippedBoundsInRoot().right.value
        val secondRowLeft = composeRule.onNodeWithTag(BeforeDeleteTags.cell(4)).getUnclippedBoundsInRoot().left.value
        assertTrue("fourth photo starts a new row", secondRowLeft < firstRowRight)
    }

    @Test
    fun `a locked grid does not toggle`() {
        show(initial(150_000_000, deleting = true))

        composeRule.onNodeWithTag(BeforeDeleteTags.cell(1)).assertIsNotEnabled()
    }

    // --- the text and the button ---

    @Test
    fun `the button shows the count and the size of the marked photos`() {
        show(initial(150_000_000, 250_000_000))

        button().assertTextEquals("Удалить 2 фото · 400 МБ")
    }

    @Test
    fun `the button follows the marks`() {
        show(initial(150_000_000, 250_000_000))

        composeRule.onNodeWithTag(BeforeDeleteTags.cell(2)).performClick()
        button().assertTextEquals("Удалить 1 фото · 150 МБ")

        composeRule.onNodeWithTag(BeforeDeleteTags.cell(1)).performClick()
        button().assertTextEquals("Готово")
    }

    @Test
    fun `a size from a gigabyte up has a comma and ГБ`() {
        show(initial(1_500_000_000, 1_000_000_000))

        button().assertTextEquals("Удалить 2 фото · 2,5 ГБ")
    }

    @Test
    fun `the text above the grid counts the marked photos`() {
        show(initial(1, 2, 3))

        composeRule.onNodeWithTag(BeforeDeleteTags.INTRO)
            .assertTextEquals("Отмечено на удаление 3 фото. Нажми на фото, чтобы оставить его.")

        composeRule.onNodeWithTag(BeforeDeleteTags.cell(1)).performClick()
        composeRule.onNodeWithTag(BeforeDeleteTags.INTRO)
            .assertTextEquals("Отмечено на удаление 2 фото. Нажми на фото, чтобы оставить его.")
    }

    @Test
    fun `with nothing marked the text says so`() {
        show(initial(1))

        composeRule.onNodeWithTag(BeforeDeleteTags.cell(1)).performClick()

        composeRule.onNodeWithTag(BeforeDeleteTags.INTRO)
            .assertTextEquals("Ничего не отмечено. Нажми на фото, чтобы отметить его снова.")
    }

    @Test
    fun `the button sends DeleteClicked, and is off while deleting`() {
        show(initial(150_000_000))

        button().assertIsEnabled().performClick()

        assertEquals(listOf(BeforeDeleteIntent.DeleteClicked), intents)
    }

    @Test
    fun `the button is disabled while a delete is on its way`() {
        show(initial(150_000_000, deleting = true))

        button().assertIsNotEnabled()
    }

    @Test
    fun `the title and the back arrow are on top`() {
        show(initial(1))

        composeRule.onNodeWithText("Перед удалением").assertExists()
        composeRule.onNodeWithTag(BeforeDeleteTags.BACK).performClick()

        assertEquals(1, backs)
    }

    // --- our own dialog below API 30 ---

    @Test
    fun `the dialog asks about the marked photos and sends the answer`() {
        show(initial(1, 2, dialog = true))

        composeRule.onNodeWithText("Удалить 2 фото безвозвратно?").assertExists()
        composeRule.onNodeWithTag(BeforeDeleteTags.DIALOG_CONFIRM).performClick()

        assertEquals(listOf(BeforeDeleteIntent.PermanentDeleteConfirmed), intents)
    }

    @Test
    fun `dismissing the dialog sends the dismissal and hides it`() {
        show(initial(1, 2, dialog = true))

        composeRule.onNodeWithTag(BeforeDeleteTags.DIALOG_DISMISS).performClick()

        assertEquals(listOf(BeforeDeleteIntent.PermanentDeleteDismissed), intents)
        composeRule.onNodeWithText("Удалить 2 фото безвозвратно?").assertDoesNotExist()
    }

    @Test
    fun `there is no dialog unless the state asks for it`() {
        show(initial(1, 2))

        composeRule.onNodeWithTag(BeforeDeleteTags.DIALOG_CONFIRM).assertDoesNotExist()
    }
}
