package com.kllxx.memorylane.reminder

import android.content.Intent
import com.kllxx.memorylane.core.common.di.ApplicationScope
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
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
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * [ReminderReceiver] through the real Hilt graph and the real settings (a DataStore under Robolectric); the
 * scheduler, the notifier and [TodayReview] are fakes. The application scope runs on the real default dispatcher, so
 * the helper waits for its children.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ReminderReceiverTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val scheduler: ReminderScheduler = mockk(relaxed = true)

    @BindValue
    @JvmField
    val notifier: ReminderNotifier = mockk(relaxed = true)

    @BindValue
    @JvmField
    val todayReview: TodayReview = mockk()

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
        coEvery { todayReview.photosToReview() } returns 3
    }

    @After
    fun tearDown() = Timber.uprootAll()

    private fun remind(action: String = ReminderConstants.ACTION_REMIND) =
        deliver(ReminderReceiver::class.java, action, appScope)

    private fun givenSetUp(notificationsEnabled: Boolean = true, onboardingCompleted: Boolean = true) = runBlocking {
        settings.setNotificationsEnabled(notificationsEnabled)
        settings.setOnboardingCompleted(onboardingCompleted)
    }

    private fun currentSettings(): AppSettings = runBlocking { settings.getSettings().first() }

    @Test
    fun `an action that is not the reminder is ignored`() {
        givenSetUp()

        remind(action = Intent.ACTION_BATTERY_LOW)

        verify(exactly = 0) { scheduler.apply(any()) }
        verify(exactly = 0) { notifier.post(any()) }
        coVerify(exactly = 0) { todayReview.photosToReview() }
    }

    @Test
    fun `photos to review set the alarm for the next day and post their count`() {
        givenSetUp()

        remind()

        verifyOrder {
            scheduler.apply(currentSettings())
            notifier.post(3)
        }
    }

    @Test
    fun `nothing to review sets the alarm again and posts nothing`() {
        givenSetUp()
        coEvery { todayReview.photosToReview() } returns 0

        remind()

        verify(exactly = 1) { scheduler.apply(any()) }
        verify(exactly = 0) { notifier.post(any()) }
    }

    @Test
    fun `a reminder that is switched off hands the settings to the scheduler and counts nothing`() {
        givenSetUp(notificationsEnabled = false)

        remind()

        verify(exactly = 1) { scheduler.apply(currentSettings()) }
        coVerify(exactly = 0) { todayReview.photosToReview() }
        verify(exactly = 0) { notifier.post(any()) }
    }

    @Test
    fun `an app that is not set up yet counts nothing and posts nothing`() {
        givenSetUp(onboardingCompleted = false)

        remind()

        verify(exactly = 1) { scheduler.apply(any()) }
        coVerify(exactly = 0) { todayReview.photosToReview() }
        verify(exactly = 0) { notifier.post(any()) }
    }

    @Test
    fun `a scheduler that fails is logged, nothing is posted and the next reminder still works`() {
        givenSetUp()
        every { scheduler.apply(any()) } throws IOException("alarm service gone")

        remind()

        assertEquals(1, log.errors().size)
        assertEquals("Could not remind", log.errors().single().message)
        assertTrue(log.errors().single().throwable is IOException)
        verify(exactly = 0) { notifier.post(any()) }

        every { scheduler.apply(any()) } just runs
        remind()

        verify(exactly = 1) { notifier.post(3) }
    }

    @Test
    fun `a failed count is logged and nothing is posted`() {
        givenSetUp()
        coEvery { todayReview.photosToReview() } throws IllegalStateException("db closed")

        remind()

        assertEquals(1, log.errors().size)
        assertTrue(log.errors().single().throwable is IllegalStateException)
        verify(exactly = 0) { notifier.post(any()) }
    }

    @Test
    fun `a cancellation is not logged as an error and posts nothing`() {
        givenSetUp()
        every { scheduler.apply(any()) } throws CancellationException("scope gone")

        remind()

        assertTrue(log.errors().isEmpty())
        verify(exactly = 0) { notifier.post(any()) }
    }
}
