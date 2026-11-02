package com.kllxx.memorylane.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A settings row with a title alone has the title centred between the icon and the trailing content. */
@RunWith(RobolectricTestRunner::class)
class SettingsRowAlignmentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun centre(node: SemanticsNodeInteraction) = node.getBoundsInRoot().let { (it.top + it.bottom) / 2 }

    private fun show(subtitle: String?) {
        composeRule.setContent {
            MemoryLaneTheme {
                SettingsRow(
                    icon = Icons.Filled.Schedule,
                    title = "Title",
                    subtitle = subtitle,
                    trailing = { Box(Modifier.size(width = 52.dp, height = 32.dp).testTag("trailing")) },
                )
            }
        }
    }

    private fun assertCentred() {
        val title = centre(composeRule.onNode(hasText("Title"), useUnmergedTree = true))
        val icon = centre(composeRule.onNode(hasTestTag(SETTINGS_ROW_ICON_TAG), useUnmergedTree = true))
        val trailing = centre(composeRule.onNode(hasTestTag("trailing"), useUnmergedTree = true))

        assertTrue("title $title, icon $icon", abs((title - icon).value) <= 0.5f)
        assertTrue("title $title, trailing $trailing", abs((title - trailing).value) <= 0.5f)
    }

    @Test
    fun `a title alone is centred between the icon and the trailing content`() {
        show(subtitle = null)

        assertCentred()
    }

    @Test
    fun `an empty subtitle takes no line and leaves the title centred`() {
        show(subtitle = "")

        assertCentred()
    }
}
