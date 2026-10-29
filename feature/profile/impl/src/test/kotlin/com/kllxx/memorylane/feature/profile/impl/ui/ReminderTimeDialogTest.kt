package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithContentDescription
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-rRU-w360dp-h800dp-xxhdpi")
class ReminderTimeDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val confirmed = mutableListOf<LocalTime>()
    private var dismissed = 0

    private fun show(initial: LocalTime = LocalTime.of(20, 0), is24Hour: Boolean = true) {
        composeRule.setContent {
            MemoryLaneTheme {
                ReminderTimeDialog(
                    initial = initial,
                    is24Hour = is24Hour,
                    onConfirm = { confirmed += it },
                    onDismiss = { dismissed++ },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the dialog has the title and the two buttons`() {
        show()

        composeRule.onNodeWithText("Время напоминания").assertExists()
        composeRule.onNodeWithText("Отмена").assertExists()
        composeRule.onNodeWithText("Готово").assertExists()
    }

    @Test
    fun `the time is typed in two fields, there is no dial and no switch to one`() {
        show()

        // The hour and the minute are fields to type in; the dial and its switch are not there
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        composeRule.onAllNodesWithText("Выберите час").assertCountEquals(0)
        composeRule.onAllNodesWithText("Выберите минуты").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Переключиться на сенсорный режим").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Переключиться на режим ввода текста").assertDoesNotExist()
    }

    @Test
    fun `the time in 24 hours has no AM or PM`() {
        show(is24Hour = true)

        composeRule.onAllNodesWithText("AM").assertCountEquals(0)
        composeRule.onAllNodesWithText("PM").assertCountEquals(0)
    }

    @Test
    fun `the entered time is confirmed`() {
        show(LocalTime.of(20, 0))
        val fields = composeRule.onAllNodes(hasSetTextAction())

        fields[0].performTextReplacement("07")
        fields[1].performTextReplacement("45")
        composeRule.onNodeWithText("Готово").performClick()

        assertEquals(listOf(LocalTime.of(7, 45)), confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun `without a change the initial time is confirmed`() {
        show(LocalTime.of(6, 5))

        composeRule.onNodeWithText("Готово").performClick()

        assertEquals(listOf(LocalTime.of(6, 5)), confirmed)
    }

    @Test
    fun `cancel keeps the old time and confirms nothing`() {
        show(LocalTime.of(20, 0))
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("03")

        composeRule.onNodeWithText("Отмена").performClick()

        assertTrue(confirmed.isEmpty())
        assertEquals(1, dismissed)
    }
}
