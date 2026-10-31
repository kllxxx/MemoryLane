package com.kllxx.memorylane.feature.swipe.impl.result

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kllxx.memorylane.core.domain.format.FormattedSize
import com.kllxx.memorylane.core.domain.format.SizeUnit
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.result.ui.ResultTags
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
import java.time.LocalDate

/** The stateful [ResultScreen] with a fake ViewModel. */
@RunWith(RobolectricTestRunner::class)
class ResultScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val uiState = MutableStateFlow(
        ResultState(
            deck = DeckKey.Day(LocalDate.of(2026, 10, 28)),
            deletedCount = 11,
            keptCount = 37,
            totalCount = 48,
            freed = FormattedSize("320", SizeUnit.MB),
            streakDays = 8,
        ),
    )
    private val sent = Channel<ResultEffect>(Channel.UNLIMITED)
    private val viewModel = mockk<ResultViewModel>(relaxed = true) {
        every { state } returns uiState
        every { effects } returns sent.receiveAsFlow()
    }

    private fun show(onNavigateToCalendar: () -> Unit = {}) {
        rule.setContent {
            MemoryLaneTheme { ResultScreen(onNavigateToCalendar = onNavigateToCalendar, viewModel = viewModel) }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the navigate effect calls the callback once`() {
        var calls = 0
        show { calls++ }

        sent.trySend(ResultEffect.NavigateToCalendar)
        rule.waitForIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `the done button sends the done intent`() {
        show()

        rule.onNodeWithTag(ResultTags.DONE).performClick()

        verify(exactly = 1) { viewModel.onIntent(ResultIntent.Done) }
    }

    @Test
    fun `back leaves like done does, through the ViewModel`() {
        show()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        verify(exactly = 1) { viewModel.onIntent(ResultIntent.Done) }
    }

    @Test
    fun `nothing leaves before an effect or a click`() {
        var calls = 0
        show { calls++ }

        assertEquals(0, calls)
    }
}
