package com.kllxx.memorylane

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.photos.domain.usecase.SyncLocationPermission
import com.kllxx.memorylane.reminder.ReminderConstants
import com.kllxx.memorylane.reminder.TodayReview
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import javax.inject.Inject

/**
 * [MainActivity] in the real Hilt graph with real settings; [TodayReview] and [SyncLocationPermission] are fakes, so
 * what the activity asks of the [AppViewModel] can be counted. What the navigation does with the answer is covered by
 * `AppViewModelTest` and `AppNavHostTest`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MainActivityTest {

    private val hilt = HiltAndroidRule(this)
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @BindValue
    @JvmField
    val todayReview: TodayReview = mockk()

    @BindValue
    @JvmField
    val syncLocationPermission: SyncLocationPermission = mockk()

    @Inject
    lateinit var settings: AppSettingsRepository

    @Before
    fun setUp() {
        hilt.inject()
        // Nothing to review, so a tap is counted but the app stays on Calendar: the real Swipe screen keeps the main looper busy,
        // and what the navigation does with a tap is covered by AppViewModelTest and AppNavHostTest
        coEvery { todayReview.photosToReview(any<LocalDate>()) } returns 0
        coEvery { syncLocationPermission.invoke() } just runs
    }

    private fun givenOnboardingCompleted(completed: Boolean) = runBlocking { settings.setOnboardingCompleted(completed) }

    // The scenario is deliberately never closed (no `.use {}`, no `close()`):
    // - `ActivityScenario.close()` moves the activity to DESTROYED and then waits for that on the test thread. Under
    //   Robolectric the test thread is the main thread, and its looper is paused, so the wait only ends if the looper
    //   is idle.
    // - With the real screens inside the activity it did not end: a thread dump of the hung run showed the test thread in
    //   `close()` -> `waitForActivityToBecomeAnyOf`, and the run had to be killed.
    // - Robolectric destroys the activity and the whole application with the end of each test anyway, so nothing leaks
    //   into the next test.
    private fun launch(intent: Intent? = null): ActivityScenario<MainActivity> {
        val launchIntent = (intent ?: Intent()).setClassName(
            RuntimeEnvironment.getApplication(),
            MainActivity::class.java.name,
        )
        return ActivityScenario.launch<MainActivity>(launchIntent)
    }

    private fun openToday() = Intent(ReminderConstants.ACTION_OPEN_TODAY)

    /** What the system does when the reminder is tapped while the activity is up: it delivers the intent to onNewIntent. */
    private fun newIntent(scenario: ActivityScenario<MainActivity>, intent: Intent) {
        scenario.onActivity { activity ->
            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, intent)
        }
    }

    private fun verifyReminderTaps(times: Int) {
        coVerify(timeout = 5_000, exactly = times) { todayReview.photosToReview(any<LocalDate>()) }
    }

    // --- what is shown ---

    @Test
    fun `a set up app shows the app with the bottom bar once the start is resolved`() {
        givenOnboardingCompleted(true)

        launch().let {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithContentDescription("Calendar").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Calendar").assertExists()
        }
    }

    @Test
    fun `a new app shows onboarding and no bottom bar`() {
        givenOnboardingCompleted(false)

        launch().let {
            compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("Пропустить").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Пропустить").assertExists()
            compose.onNodeWithContentDescription("Calendar").assertDoesNotExist()
        }
    }

    // --- the reminder ---

    @Test
    fun `an ordinary start does not ask for today`() {
        givenOnboardingCompleted(true)

        launch().let {
            compose.waitForIdle()
            verifyReminderTaps(0)
        }
    }

    @Test
    fun `a start from the reminder asks once for today and takes the action off the intent`() {
        givenOnboardingCompleted(true)

        launch(openToday()).let { scenario ->
            verifyReminderTaps(1)
            scenario.onActivity { assertNull(it.intent.action) }
        }
    }

    @Test
    fun `a tap on the reminder while the app is open asks again`() {
        givenOnboardingCompleted(true)

        launch().let { scenario ->
            compose.waitForIdle()
            newIntent(scenario, openToday())

            verifyReminderTaps(1)
            scenario.onActivity { assertNull(it.intent.action) }
        }
    }

    @Test
    fun `another intent action while the app is open is not a reminder tap`() {
        givenOnboardingCompleted(true)

        launch().let { scenario ->
            compose.waitForIdle()
            newIntent(scenario, Intent(Intent.ACTION_VIEW))
            compose.waitForIdle()

            verifyReminderTaps(0)
        }
    }

    @Test
    fun `a recreation of the activity is not a second tap`() {
        givenOnboardingCompleted(true)

        launch(openToday()).let { scenario ->
            verifyReminderTaps(1)

            scenario.recreate()
            compose.waitForIdle()

            verifyReminderTaps(1)
        }
    }

    @Test
    fun `a reminder tap before onboarding is finished does not ask for today`() {
        givenOnboardingCompleted(false)

        launch(openToday()).let {
            compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("Пропустить").fetchSemanticsNodes().isNotEmpty() }
            // The activity asks the ViewModel; the ViewModel drops it before the app is set up
            verifyReminderTaps(0)
        }
    }

    // --- the permissions ---

    @Test
    fun `every time the app comes to the front the permissions are synced`() {
        givenOnboardingCompleted(true)

        launch().let { scenario ->
            compose.waitForIdle()
            coVerify(timeout = 5_000, exactly = 1) { syncLocationPermission.invoke() }

            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()

            coVerify(timeout = 5_000, exactly = 2) { syncLocationPermission.invoke() }
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
