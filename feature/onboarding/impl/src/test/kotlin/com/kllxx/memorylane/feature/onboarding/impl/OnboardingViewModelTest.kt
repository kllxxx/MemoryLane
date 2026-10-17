package com.kllxx.memorylane.feature.onboarding.impl

import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        viewModel = OnboardingViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
            viewModel.onIntent(OnboardingIntent.NextPage)
            viewModel.onIntent(OnboardingIntent.NextPage)
            advanceUntilIdle()
            expectNoEvents()
            viewModel.onIntent(OnboardingIntent.NextPage)
            advanceUntilIdle()
            assertEquals(OnboardingEffect.NavigateToSwipe, awaitItem())
        }
        coVerify(exactly = 1) { repository.setOnboardingCompleted(true) }
    }

    @Test
    fun `Skip completes onboarding`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.Skip)
            advanceUntilIdle()
            assertEquals(OnboardingEffect.NavigateToSwipe, awaitItem())
        }
        coVerify(exactly = 1) { repository.setOnboardingCompleted(true) }
    }

    @Test
    fun `RequestPermissions sets loading and emits launcher effect`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.RequestPermissions)
            assertTrue(viewModel.state.value.isLoading)
            assertEquals(OnboardingEffect.RequestPermissionLauncher, awaitItem())
        }
    }

    @Test
    fun `PermissionResult granted completes onboarding`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.PermissionResult(true))
            advanceUntilIdle()
            assertTrue(viewModel.state.value.permissionsGranted)
            assertEquals(OnboardingEffect.NavigateToSwipe, awaitItem())
        }
        coVerify(exactly = 1) { repository.setOnboardingCompleted(true) }
    }

    @Test
    fun `first denial shows rationale without completing`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.PermissionResult(false))
            advanceUntilIdle()
            assertTrue(viewModel.state.value.showPermissionRationale)
            expectNoEvents()
        }
        coVerify(exactly = 0) { repository.setOnboardingCompleted(true) }
    }

    @Test
    fun `second denial hides rationale and completes onboarding`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.PermissionResult(false))
            viewModel.onIntent(OnboardingIntent.PermissionResult(false))
            advanceUntilIdle()
            assertFalse(viewModel.state.value.showPermissionRationale)
            assertEquals(OnboardingEffect.NavigateToSwipe, awaitItem())
        }
        coVerify(exactly = 1) { repository.setOnboardingCompleted(true) }
    }

    @Test
    fun `DismissRationale hides rationale`() = runTest(dispatcher) {
        viewModel.onIntent(OnboardingIntent.PermissionResult(false))
        viewModel.onIntent(OnboardingIntent.DismissRationale)
        assertFalse(viewModel.state.value.showPermissionRationale)
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
    fun `an effect sent before anyone collects is not lost`() = runTest(dispatcher) {
        viewModel.onIntent(OnboardingIntent.RequestPermissions)
        viewModel.effects.test {
            assertEquals(OnboardingEffect.RequestPermissionLauncher, awaitItem())
        }
    }
}
