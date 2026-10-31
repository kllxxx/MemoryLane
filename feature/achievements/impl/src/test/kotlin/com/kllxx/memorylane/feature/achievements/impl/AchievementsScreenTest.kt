package com.kllxx.memorylane.feature.achievements.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.achievements.impl.ui.GO_TO_CALENDAR_TAG
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The stateful [AchievementsScreen] with a fake ViewModel: what its effects and the clicks do outside the screen. */
@RunWith(RobolectricTestRunner::class)
class AchievementsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val uiState = MutableStateFlow<AchievementsState>(AchievementsState.Empty)
    private val sent = Channel<AchievementsEffect>(Channel.UNLIMITED)
    private val viewModel = mockk<AchievementsViewModel>(relaxed = true) {
        every { state } returns uiState
        every { effects } returns sent.receiveAsFlow()
    }

    @Test
    fun `the navigate effect calls the callback once`() {
        var calls = 0
        rule.setContent { MemoryLaneTheme { AchievementsScreen(onNavigateToCalendar = { calls++ }, viewModel = viewModel) } }

        sent.trySend(AchievementsEffect.NavigateToCalendar)
        rule.waitForIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `an effect goes to the latest callback, not the one the screen started with`() {
        val calls = mutableListOf<String>()
        var target by mutableStateOf("first")
        rule.setContent {
            MemoryLaneTheme {
                AchievementsScreen(onNavigateToCalendar = { calls += target }, viewModel = viewModel)
            }
        }
        rule.waitForIdle()

        target = "second"
        rule.waitForIdle()
        sent.trySend(AchievementsEffect.NavigateToCalendar)
        rule.waitForIdle()

        assertEquals(listOf("second"), calls)
    }

    @Test
    fun `the button of the empty state sends the calendar intent to the ViewModel`() {
        rule.setContent { MemoryLaneTheme { AchievementsScreen(onNavigateToCalendar = {}, viewModel = viewModel) } }

        rule.onNodeWithTag(GO_TO_CALENDAR_TAG).performClick()

        verify(exactly = 1) { viewModel.onIntent(AchievementsIntent.GoToCalendarClicked) }
    }

    @Test
    fun `nothing is called before an effect arrives`() {
        var calls = 0
        rule.setContent { MemoryLaneTheme { AchievementsScreen(onNavigateToCalendar = { calls++ }, viewModel = viewModel) } }
        rule.waitForIdle()

        assertEquals(0, calls)
    }
}
