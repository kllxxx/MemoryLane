package com.kllxx.memorylane.reminder

import android.content.Intent
import com.kllxx.memorylane.core.common.di.ApplicationScope
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** [ReminderBootReceiver]: every event that moves the clock or the app sets the alarm again from the stored settings. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ReminderBootReceiverTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val scheduler: ReminderScheduler = mockk(relaxed = true)

    @Inject
    lateinit var settings: AppSettingsRepository

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    private val log = CapturingTree()

    @Before
    fun setUp() {
        hilt.inject()
        Timber.plant(log)
        runBlocking {
            settings.setOnboardingCompleted(true)
            settings.setReminderTime(LocalTime.of(7, 15))
        }
    }

    @After
    fun tearDown() = Timber.uprootAll()

    private fun receive(action: String) = deliver(ReminderBootReceiver::class.java, action, appScope)

    private fun currentSettings(): AppSettings = runBlocking { settings.getSettings().first() }

    @Test
    fun `every event that moves the clock or the app sets the alarm from the stored settings`() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        ).forEach { action ->
            io.mockk.clearMocks(scheduler, answers = false)

            receive(action)

            verify(exactly = 1) { scheduler.apply(currentSettings()) }
        }
    }

    @Test
    fun `the settings handed over are the stored ones, with the time of the reminder`() {
        receive(Intent.ACTION_BOOT_COMPLETED)

        verify(exactly = 1) { scheduler.apply(match { it.reminderTime == LocalTime.of(7, 15) && it.onboardingCompleted }) }
    }

    @Test
    fun `any other action is ignored`() {
        receive(Intent.ACTION_BATTERY_LOW)
        receive(ReminderConstants.ACTION_REMIND)

        verify(exactly = 0) { scheduler.apply(any()) }
    }

    @Test
    fun `a scheduler that fails is logged and the next event still works`() {
        every { scheduler.apply(any()) } throws IOException("alarm service gone")

        receive(Intent.ACTION_BOOT_COMPLETED)

        assertEquals(1, log.errors().size)
        assertEquals("Could not set the reminder again", log.errors().single().message)
        assertTrue(log.errors().single().throwable is IOException)

        every { scheduler.apply(any()) } just runs
        receive(Intent.ACTION_TIME_CHANGED)

        verify(exactly = 2) { scheduler.apply(any()) }
    }

    @Test
    fun `a cancellation is not logged as an error`() {
        every { scheduler.apply(any()) } throws CancellationException("scope gone")

        receive(Intent.ACTION_BOOT_COMPLETED)

        assertTrue(log.errors().isEmpty())
    }
}
