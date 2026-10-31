package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kllxx.memorylane.core.common.UiError
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.RecordingActivityResultRegistry
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.ui.BeforeDeleteTags
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowToast
import java.time.LocalDate

/** The stateful [BeforeDeleteScreen] with a fake ViewModel and a registry that answers the launchers of the system. */
@RunWith(RobolectricTestRunner::class)
class BeforeDeleteScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val deck = DeckKey.Day(LocalDate.of(2026, 10, 28))
    private val uiState = MutableStateFlow(BeforeDeleteState(isLoading = false, currentDeck = deck))
    private val sent = Channel<BeforeDeleteEffect>(Channel.UNLIMITED)
    private val viewModel = mockk<BeforeDeleteViewModel>(relaxed = true) {
        every { state } returns uiState
        every { effects } returns sent.receiveAsFlow()
    }
    private val calls = mutableListOf<String>()

    private fun show(registry: RecordingActivityResultRegistry) {
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner) {
                MemoryLaneTheme {
                    BeforeDeleteScreen(
                        onBack = { calls += "back" },
                        onNavigateToResult = { d, deleted, freed, kept, total ->
                            calls += "result $d $deleted $freed $kept $total"
                        },
                        onNavigateToCalendar = { calls += "calendar" },
                        viewModel = viewModel,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun send(effect: BeforeDeleteEffect) {
        sent.trySend(effect)
        rule.waitForIdle()
    }

    private fun registry(resultCode: Int = Activity.RESULT_OK, granted: Boolean = true) =
        RecordingActivityResultRegistry { contract, _ ->
            when (contract) {
                is ActivityResultContracts.StartIntentSenderForResult -> ActivityResult(resultCode, null)
                is ActivityResultContracts.RequestPermission -> granted
                else -> error("unexpected contract $contract")
            }
        }

    // --- the system dialog of the trash request ---

    @Test
    fun `the trash request is launched with the intent sender and a confirmation is passed on`() {
        val intentSender = mockk<IntentSender>(relaxed = true)
        val registry = registry(resultCode = Activity.RESULT_OK)
        show(registry)

        send(BeforeDeleteEffect.LaunchTrashRequest(intentSender))

        val (contract, input) = registry.launches.single()
        assertTrue(contract is ActivityResultContracts.StartIntentSenderForResult)
        assertSame(intentSender, (input as IntentSenderRequest).intentSender)
        verify(exactly = 1) { viewModel.onIntent(BeforeDeleteIntent.SystemDeleteResult(confirmed = true)) }
    }

    @Test
    fun `a trash request the user cancelled is passed on as not confirmed`() {
        val registry = registry(resultCode = Activity.RESULT_CANCELED)
        show(registry)

        send(BeforeDeleteEffect.LaunchTrashRequest(mockk(relaxed = true)))

        verify(exactly = 1) { viewModel.onIntent(BeforeDeleteIntent.SystemDeleteResult(confirmed = false)) }
        verify(exactly = 0) { viewModel.onIntent(BeforeDeleteIntent.SystemDeleteResult(confirmed = true)) }
    }

    // --- the write permission of API 24-29 ---

    @Test
    fun `the write permission is asked for and a grant is passed on`() {
        val registry = registry(granted = true)
        show(registry)

        send(BeforeDeleteEffect.RequestWritePermission)

        val (contract, input) = registry.launches.single()
        assertTrue(contract is ActivityResultContracts.RequestPermission)
        assertEquals(Manifest.permission.WRITE_EXTERNAL_STORAGE, input)
        verify(exactly = 1) { viewModel.onIntent(BeforeDeleteIntent.WritePermissionResult(granted = true)) }
    }

    @Test
    fun `a refused write permission is passed on as refused`() {
        show(registry(granted = false))

        send(BeforeDeleteEffect.RequestWritePermission)

        verify(exactly = 1) { viewModel.onIntent(BeforeDeleteIntent.WritePermissionResult(granted = false)) }
    }

    // --- leaving ---

    @Test
    fun `a finished deletion goes to the result with every number`() {
        show(registry())

        send(BeforeDeleteEffect.NavigateToResult(deck, deletedCount = 3, freedBytes = 1_500L, keptCount = 6, totalCount = 9))

        assertEquals(listOf("result $deck 3 1500 6 9"), calls)
    }

    @Test
    fun `nothing to review goes to calendar`() {
        show(registry())

        send(BeforeDeleteEffect.NavigateToCalendar)

        assertEquals(listOf("calendar"), calls)
    }

    @Test
    fun `the back button of the screen calls the back callback`() {
        show(registry())

        rule.onNodeWithTag(BeforeDeleteTags.BACK).performClick()

        assertEquals(listOf("back"), calls)
    }

    // --- messages ---

    @Test
    fun `a failed delete shows its message as a toast`() {
        show(registry())

        send(BeforeDeleteEffect.ShowError(UiError.Generic))

        assertEquals(
            RuntimeEnvironment.getApplication().getString(UiError.Generic.message),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `a refused permission shows its own message as a toast`() {
        show(registry())

        send(BeforeDeleteEffect.ShowPermissionDenied)

        assertEquals(
            RuntimeEnvironment.getApplication().getString(R.string.before_delete_permission_denied),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `nothing is launched or called before an effect arrives`() {
        val registry = registry()
        show(registry)

        assertTrue(registry.launches.isEmpty())
        assertTrue(calls.isEmpty())
    }
}
