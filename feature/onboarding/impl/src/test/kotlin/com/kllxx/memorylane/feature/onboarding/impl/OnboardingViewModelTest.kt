package com.kllxx.memorylane.feature.onboarding.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.UiError
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: AppSettingsRepository
    private lateinit var viewModel: OnboardingViewModel
    private lateinit var savedStateHandle: SavedStateHandle
    private val errorHandler = mockk<ErrorHandler> { every { handle(any(), any()) } returns UiError.Generic }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle()
        viewModel = OnboardingViewModel(repository, savedStateHandle, errorHandler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun goToPhotoPage() {
        repeat(OnboardingConstants.PHOTO_PAGE) { viewModel.onIntent(OnboardingIntent.NextPage) }
    }

    @Test
    fun `NextPage and PreviousPage update state`() = runTest(dispatcher) {
        viewModel.onIntent(OnboardingIntent.NextPage)
        assertEquals(1, viewModel.state.value.currentPage)
        viewModel.onIntent(OnboardingIntent.PreviousPage)
        assertEquals(0, viewModel.state.value.currentPage)
    }

    @Test
    fun `NextPage on last page completes onboarding`() = runTest(dispatcher) {
        viewModel.effects.test {
            repeat(3) { viewModel.onIntent(OnboardingIntent.NextPage) }
            advanceUntilIdle()
            expectNoEvents()
            viewModel.onIntent(OnboardingIntent.NextPage)
            advanceUntilIdle()
            assertEquals(OnboardingEffect.NavigateToSwipe, awaitItem())
        }
        coVerify(exactly = 1) { repository.setOnboardingCompleted(true) }
    }

    @Test
    fun `Skip completes onboarding from any page`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.NextPage)
            viewModel.onIntent(OnboardingIntent.Skip)
            advanceUntilIdle()
            assertEquals(OnboardingEffect.NavigateToSwipe, awaitItem())
        }
        coVerify(exactly = 1) { repository.setOnboardingCompleted(true) }
    }

    @Test
    fun `RequestPhotoPermission sets loading and emits launcher effect`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.RequestPhotoPermission)
            assertTrue(viewModel.state.value.isLoading)
            assertEquals(OnboardingEffect.RequestPhotoPermissionLauncher, awaitItem())
        }
    }

    @Test
    fun `RequestNotificationPermission emits launcher effect`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.RequestNotificationPermission)
            assertEquals(OnboardingEffect.RequestNotificationPermissionLauncher, awaitItem())
        }
    }

    @Test
    fun `photo permission granted moves to the notification page without completing`() = runTest(dispatcher) {
        goToPhotoPage()
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.PhotoPermissionResult(true))
            advanceUntilIdle()
            assertTrue(viewModel.state.value.permissionsGranted)
            assertEquals(OnboardingConstants.NOTIFICATION_PAGE, viewModel.state.value.currentPage)
            expectNoEvents()
        }
        coVerify(exactly = 0) { repository.setOnboardingCompleted(true) }
    }

    @Test
    fun `first photo denial stays on the page without rationale`() = runTest(dispatcher) {
        goToPhotoPage()
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.PhotoPermissionResult(false))
            advanceUntilIdle()
            assertEquals(OnboardingConstants.PHOTO_PAGE, viewModel.state.value.currentPage)
            assertFalse(viewModel.state.value.showPermissionRationale)
            expectNoEvents()
        }
        coVerify(exactly = 0) { repository.setOnboardingCompleted(true) }
    }

    @Test
    fun `second photo denial shows rationale and still does not complete`() = runTest(dispatcher) {
        goToPhotoPage()
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.PhotoPermissionResult(false))
            viewModel.onIntent(OnboardingIntent.PhotoPermissionResult(false))
            advanceUntilIdle()
            assertTrue(viewModel.state.value.showPermissionRationale)
            assertEquals(OnboardingConstants.PHOTO_PAGE, viewModel.state.value.currentPage)
            expectNoEvents()
        }
        coVerify(exactly = 0) { repository.setOnboardingCompleted(true) }
    }

    @Test
    fun `photo denials are saved`() = runTest(dispatcher) {
        viewModel.onIntent(OnboardingIntent.PhotoPermissionResult(false))
        assertEquals(1, savedStateHandle.get<Int>(OnboardingViewModel.PERMISSION_DENIALS_KEY))
    }

    @Test
    fun `a restored denial makes the next one reach the rationale`() = runTest(dispatcher) {
        savedStateHandle = SavedStateHandle(mapOf(OnboardingViewModel.PERMISSION_DENIALS_KEY to 1))
        viewModel = OnboardingViewModel(repository, savedStateHandle, errorHandler)

        viewModel.onIntent(OnboardingIntent.PhotoPermissionResult(false))
        assertTrue(viewModel.state.value.showPermissionRationale)
    }

    @Test
    fun `OpenSettings emits the settings effect`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.OpenSettings)
            assertEquals(OnboardingEffect.OpenAppSettings, awaitItem())
        }
    }

    @Test
    fun `DismissRationale hides rationale and moves on`() = runTest(dispatcher) {
        goToPhotoPage()
        repeat(2) { viewModel.onIntent(OnboardingIntent.PhotoPermissionResult(false)) }
        viewModel.onIntent(OnboardingIntent.DismissRationale)
        assertFalse(viewModel.state.value.showPermissionRationale)
        assertEquals(OnboardingConstants.NOTIFICATION_PAGE, viewModel.state.value.currentPage)
    }

    @Test
    fun `notification result completes onboarding whether granted or denied`() = runTest(dispatcher) {
        listOf(true, false).forEach { granted ->
            viewModel.effects.test {
                viewModel.onIntent(OnboardingIntent.NotificationPermissionResult(granted))
                advanceUntilIdle()
                assertEquals(OnboardingEffect.NavigateToSwipe, awaitItem())
            }
        }
        coVerify(exactly = 2) { repository.setOnboardingCompleted(true) }
    }

    @Test
    fun `a failing flag write still lets the user move on`() = runTest(dispatcher) {
        coEvery { repository.setOnboardingCompleted(true) } throws IllegalStateException("disk full")
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.Skip)
            advanceUntilIdle()
            assertEquals(OnboardingEffect.NavigateToSwipe, awaitItem())
        }
    }

    @Test
    fun `a failing flag write is logged with what failed`() = runTest(dispatcher) {
        coEvery { repository.setOnboardingCompleted(true) } throws IllegalStateException("disk full")

        viewModel.onIntent(OnboardingIntent.Skip)
        advanceUntilIdle()

        verify(exactly = 1) { errorHandler.handle(any<IllegalStateException>(), "store that onboarding is completed") }
    }

    @Test
    fun `a cancelled flag write neither navigates nor counts as an error`() = runTest(dispatcher) {
        coEvery { repository.setOnboardingCompleted(true) } coAnswers { awaitCancellation() }
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.Skip)
            advanceUntilIdle()

            viewModel.viewModelScope.cancel()
            advanceUntilIdle()

            expectNoEvents()
        }
        verify(exactly = 0) { errorHandler.handle(any(), any()) }
    }

    @Test
    fun `an effect sent before anyone collects is not lost`() = runTest(dispatcher) {
        viewModel.onIntent(OnboardingIntent.RequestPhotoPermission)
        viewModel.effects.test {
            assertEquals(OnboardingEffect.RequestPhotoPermissionLauncher, awaitItem())
        }
    }
}
