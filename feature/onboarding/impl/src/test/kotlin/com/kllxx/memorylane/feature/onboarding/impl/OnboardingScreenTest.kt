package com.kllxx.memorylane.feature.onboarding.impl

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The effects of the stateful [OnboardingScreen] with a fake ViewModel and a registry that answers the permission dialogs. */
@RunWith(RobolectricTestRunner::class)
class OnboardingScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val application = RuntimeEnvironment.getApplication()
    private val uiState = MutableStateFlow(OnboardingState())
    private val sent = Channel<OnboardingEffect>(Channel.UNLIMITED)
    private val viewModel = mockk<OnboardingViewModel>(relaxed = true) {
        every { state } returns uiState
        every { effects } returns sent.receiveAsFlow()
    }
    private var finished = 0

    /** Answers every permission of a request with [granted]. */
    private fun registry(granted: Boolean = true) = RecordingActivityResultRegistry { contract, input ->
        when (contract) {
            is ActivityResultContracts.RequestMultiplePermissions -> (input as Array<*>).associate { it as String to granted }
            else -> error("unexpected contract $contract")
        }
    }

    private fun show(registry: RecordingActivityResultRegistry = registry()) {
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner) {
                MemoryLaneTheme { OnboardingScreen(onFinished = { finished++ }, viewModel = viewModel) }
            }
        }
        rule.waitForIdle()
    }

    private fun send(effect: OnboardingEffect) {
        sent.trySend(effect)
        rule.waitForIdle()
    }

    private fun startedActivity(): Intent? = shadowOf(application).nextStartedActivity

    @Test
    fun `the last page calls the finished callback`() {
        show()

        send(OnboardingEffect.NavigateToSwipe)

        assertEquals(1, finished)
    }

    // --- the photo permission ---

    @Test
    fun `the photo permissions are asked for together and a grant is passed on`() {
        val registry = registry(granted = true)
        show(registry)

        send(OnboardingEffect.RequestPhotoPermissionLauncher)

        val (contract, input) = registry.launches.single()
        assertTrue(contract is ActivityResultContracts.RequestMultiplePermissions)
        assertEquals(OnboardingPermissions.photo(), (input as Array<*>).toList())
        verify(exactly = 1) { viewModel.onIntent(OnboardingIntent.PhotoPermissionResult(granted = true)) }
    }

    @Test
    fun `refused photo permissions are passed on as refused`() {
        show(registry(granted = false))

        send(OnboardingEffect.RequestPhotoPermissionLauncher)

        verify(exactly = 1) { viewModel.onIntent(OnboardingIntent.PhotoPermissionResult(granted = false)) }
    }

    // --- the notification permission ---

    @Test
    fun `from Android 13 the notification permission is asked for and the answer is passed on`() {
        val registry = registry(granted = true)
        show(registry)

        send(OnboardingEffect.RequestNotificationPermissionLauncher)

        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), (registry.launches.single().second as Array<*>).toList())
        verify(exactly = 1) { viewModel.onIntent(OnboardingIntent.NotificationPermissionResult(granted = true)) }
    }

    @Test
    fun `a refused notification permission is passed on as refused`() {
        show(registry(granted = false))

        send(OnboardingEffect.RequestNotificationPermissionLauncher)

        verify(exactly = 1) { viewModel.onIntent(OnboardingIntent.NotificationPermissionResult(granted = false)) }
    }

    @Test
    @Config(sdk = [30])
    fun `before Android 13 there is nothing to ask and the step counts as granted`() {
        val registry = registry()
        show(registry)

        send(OnboardingEffect.RequestNotificationPermissionLauncher)

        assertTrue(registry.launches.isEmpty())
        verify(exactly = 1) { viewModel.onIntent(OnboardingIntent.NotificationPermissionResult(granted = true)) }
    }

    // --- settings and back ---

    @Test
    fun `the settings page of the app is opened`() {
        show()

        send(OnboardingEffect.OpenAppSettings)

        val intent = startedActivity()
        assertNotNull(intent)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent!!.action)
        assertEquals("package:${application.packageName}", intent.data.toString())
    }

    @Test
    fun `back on a later page goes to the previous page`() {
        uiState.value = OnboardingState(currentPage = 2)
        show()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        verify(exactly = 1) { viewModel.onIntent(OnboardingIntent.PreviousPage) }
    }

    @Test
    fun `back on the first page is left to the system`() {
        show()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        verify(exactly = 0) { viewModel.onIntent(OnboardingIntent.PreviousPage) }
    }
}
