package com.kllxx.memorylane.feature.profile.impl

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kllxx.memorylane.core.common.UiError
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The stateful [ProfileScreen] with a fake ViewModel and a registry that answers the permission launchers. */
@RunWith(RobolectricTestRunner::class)
class ProfileScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val application = RuntimeEnvironment.getApplication()
    private val uiState = MutableStateFlow<ProfileState>(ProfileState.Content())
    private val sent = Channel<ProfileEffect>(Channel.UNLIMITED)
    private val viewModel = mockk<ProfileViewModel>(relaxed = true) {
        every { state } returns uiState
        every { effects } returns sent.receiveAsFlow()
    }
    private var onboardingCalls = 0

    private fun registry(granted: Boolean = true) = RecordingActivityResultRegistry { contract, input ->
        when (contract) {
            is ActivityResultContracts.RequestPermission -> granted
            is ActivityResultContracts.RequestMultiplePermissions ->
                (input as Array<*>).associate { it as String to granted }
            else -> error("unexpected contract $contract")
        }
    }

    private fun show(registry: RecordingActivityResultRegistry = registry()) {
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner) {
                MemoryLaneTheme { ProfileScreen(onNavigateToOnboarding = { onboardingCalls++ }, viewModel = viewModel) }
            }
        }
        rule.waitForIdle()
    }

    private fun send(effect: ProfileEffect) {
        sent.trySend(effect)
        rule.waitForIdle()
    }

    private fun startedActivity(): Intent? = shadowOf(application).nextStartedActivity

    // --- screens and settings of the system ---

    @Test
    fun `replaying onboarding calls the callback`() {
        show()

        send(ProfileEffect.NavigateToOnboarding)

        assertEquals(1, onboardingCalls)
    }

    @Test
    fun `the notification settings of the app are opened`() {
        show()

        send(ProfileEffect.OpenNotificationSettings)

        val intent = startedActivity()
        assertNotNull(intent)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent!!.action)
        assertEquals(application.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun `the settings page of the app is opened`() {
        show()

        send(ProfileEffect.OpenAppSettings)

        val intent = startedActivity()
        assertNotNull(intent)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent!!.action)
        assertEquals("package:${application.packageName}", intent.data.toString())
    }

    // --- permissions ---

    @Test
    fun `the notification permission is asked for and a grant is passed on`() {
        val registry = registry(granted = true)
        show(registry)

        send(ProfileEffect.RequestNotificationPermission)

        val (contract, input) = registry.launches.single()
        assertTrue(contract is ActivityResultContracts.RequestPermission)
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, input)
        verify(exactly = 1) { viewModel.onIntent(ProfileIntent.PermissionResult(granted = true)) }
        // The answer is followed by a look at the system, which can tell a permanent denial
        verify(atLeast = 2) { viewModel.onIntent(match { it is ProfileIntent.SystemNotificationsChecked }) }
    }

    @Test
    fun `a refused notification permission is passed on as refused`() {
        show(registry(granted = false))

        send(ProfileEffect.RequestNotificationPermission)

        verify(exactly = 1) { viewModel.onIntent(ProfileIntent.PermissionResult(granted = false)) }
    }

    @Test
    fun `the photo permissions are asked for together`() {
        val registry = registry()
        show(registry)

        send(ProfileEffect.RequestPermissions(listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION)))

        val (contract, input) = registry.launches.single()
        assertTrue(contract is ActivityResultContracts.RequestMultiplePermissions)
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION),
            input as Array<*>,
        )
    }

    @Test
    fun `coming back to the screen reads the system notifications and the permissions again`() {
        show()

        verify(atLeast = 1) { viewModel.onIntent(match { it is ProfileIntent.SystemNotificationsChecked }) }
        verify(atLeast = 1) { viewModel.onIntent(match { it is ProfileIntent.PermissionsChecked }) }
    }

    // --- support ---

    @Test
    fun `contact support opens a mail to the support address with the version, the system and the device`() {
        show()

        send(ProfileEffect.ContactSupport(versionName = "1.2.3"))

        val intent = startedActivity()
        assertNotNull(intent)
        assertEquals(Intent.ACTION_SENDTO, intent!!.action)
        assertEquals("mailto:kuravkindm@gmail.com", intent.data.toString())
        assertArrayEquals(arrayOf("kuravkindm@gmail.com"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("MemoryLane — помощь", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue(body.contains("Версия приложения: 1.2.3"))
        assertTrue(body.contains("Android: ${Build.VERSION.RELEASE}"))
        assertTrue(body.contains("Устройство: ${Build.MODEL}"))
    }

    @Test
    fun `without a mail app a message is shown and nothing is started`() {
        shadowOf(application).checkActivities(true)
        show()

        send(ProfileEffect.ContactSupport(versionName = "1.2.3"))

        assertNull(startedActivity())
        rule.onNodeWithText("Не найдено почтовое приложение").assertIsDisplayed()
    }

    // --- errors ---

    @Test
    fun `a setting that could not be stored shows its message in the snackbar`() {
        show()

        send(ProfileEffect.ShowError(UiError.Generic))

        rule.onNodeWithText(application.getString(UiError.Generic.message)).assertIsDisplayed()
    }
}
