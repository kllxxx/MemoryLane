package com.kllxx.memorylane

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import com.kllxx.memorylane.feature.calendar.impl.R as CalendarR
import com.kllxx.memorylane.feature.onboarding.impl.R as OnboardingR

/**
 * The whole app on a device, from a clean first start: the splash resolves to onboarding, «Пропустить» finishes it,
 * and Calendar is on the screen. It is a smoke test, so it checks that the screens are reached, not what they hold.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun startFromAFreshInstall() {
        // Granted up front so that Calendar shows its weekdays and no system dialog gets in the way
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        permissionsOfThisDevice().forEach { automation.grantRuntimePermission(targetContext.packageName, it) }
        // The settings live in a DataStore file; without it the app has never been opened
        File(targetContext.filesDir, "datastore").deleteRecursively()
    }

    @After
    fun closeTheActivity() {
        scenario?.close()
    }

    @Test
    fun a_first_start_goes_through_onboarding_to_calendar() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        val skip = targetContext.getString(OnboardingR.string.onboarding_skip)
        compose.waitUntil(timeoutMillis = TIMEOUT_MS) { compose.onAllNodesWithText(skip).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(skip).performClick()

        // "calendar-weekdays" is internal to the calendar feature, so the literal is repeated here
        compose.waitUntil(timeoutMillis = TIMEOUT_MS) {
            compose.onAllNodesWithTag(CALENDAR_WEEKDAYS_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(CALENDAR_WEEKDAYS_TAG).assertIsDisplayed()
        compose.onNodeWithText(targetContext.getString(CalendarR.string.calendar_title)).assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val CALENDAR_WEEKDAYS_TAG = "calendar-weekdays"

        fun permissionsOfThisDevice(): Array<String> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }.toTypedArray()
    }
}
