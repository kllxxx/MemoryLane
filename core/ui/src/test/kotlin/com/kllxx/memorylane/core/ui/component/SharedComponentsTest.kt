package com.kllxx.memorylane.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Smoke tests of the shared building blocks: they render what they are given and report the clicks they are meant to. */
@RunWith(RobolectricTestRunner::class)
class SharedComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { MemoryLaneTheme { content() } }
        rule.waitForIdle()
    }

    // --- PrimaryButton ---

    @Test
    fun `a primary button shows its text and reports a click`() {
        var clicks = 0
        show { PrimaryButton(text = "Allow", onClick = { clicks++ }) }

        rule.onNodeWithText("Allow").assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `a disabled primary button does not report a click`() {
        var clicks = 0
        show { PrimaryButton(text = "Allow", onClick = { clicks++ }, enabled = false) }

        rule.onNodeWithText("Allow").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    // --- EmptyState ---

    @Test
    fun `an empty state shows the title and the body`() {
        show {
            EmptyState(
                icon = rememberVectorPainter(Icons.Filled.Info),
                title = "Nothing here",
                body = "Come back tomorrow",
            )
        }

        rule.onNodeWithText("Nothing here").assertIsDisplayed()
        rule.onNodeWithText("Come back tomorrow").assertIsDisplayed()
    }

    @Test
    fun `an empty state shows its action when it has one`() {
        var clicks = 0
        show {
            EmptyState(
                icon = rememberVectorPainter(Icons.Filled.Info),
                title = "Nothing here",
                body = "Come back tomorrow",
                action = { PrimaryButton(text = "Retry", onClick = { clicks++ }) },
            )
        }

        rule.onNodeWithText("Retry").assertIsDisplayed().performClick()

        assertEquals(1, clicks)
    }

    // --- SettingsRow and the cards ---

    @Test
    fun `a settings row shows the title, the subtitle and the trailing content`() {
        show {
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "Reminder",
                subtitle = "Every day",
                trailing = { RowValue("20:00") },
            )
        }

        rule.onNodeWithText("Reminder").assertIsDisplayed()
        rule.onNodeWithText("Every day").assertIsDisplayed()
        rule.onNodeWithText("20:00").assertIsDisplayed()
    }

    @Test
    fun `a settings row with a click reports it`() {
        var clicks = 0
        show { SettingsRow(icon = Icons.Filled.Info, title = "Reminder", onClick = { clicks++ }) }

        rule.onNodeWithText("Reminder").assertIsEnabled().assertHasClickAction().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `a disabled settings row does not report a click`() {
        var clicks = 0
        show { SettingsRow(icon = Icons.Filled.Info, title = "Reminder", enabled = false, onClick = { clicks++ }) }

        rule.onNodeWithText("Reminder").performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun `a settings row without a click is not clickable`() {
        show { SettingsRow(icon = Icons.Filled.Info, title = "Version", subtitle = "1.2.3") }

        val clickable = rule.onNodeWithText("Version").fetchSemanticsNode().config
            .contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)

        assertTrue(!clickable)
    }

    @Test
    fun `the cards, the header and the divider show their content`() {
        show {
            Column {
                SectionHeader("Notifications")
                SectionCard { Text("inside a section card") }
                RowsCard {
                    Text("first row")
                    RowDivider()
                    Text("second row")
                }
            }
        }

        // The header is shown in capitals
        rule.onNodeWithText("NOTIFICATIONS").assertIsDisplayed()
        rule.onNodeWithText("inside a section card").assertIsDisplayed()
        rule.onNodeWithText("first row").assertIsDisplayed()
        rule.onNodeWithText("second row").assertIsDisplayed()
    }

    // --- PageIndicator ---

    @Test
    fun `a page indicator renders for any page of any count`() {
        show {
            Column {
                PageIndicator(pageCount = 4, currentPage = 0)
                PageIndicator(pageCount = 4, currentPage = 3, modifier = Modifier.size(200.dp, 20.dp))
                PageIndicator(pageCount = 1, currentPage = 0)
            }
        }

        val bounds = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(bounds.width > 0.dp && bounds.height > 0.dp)
    }
}
