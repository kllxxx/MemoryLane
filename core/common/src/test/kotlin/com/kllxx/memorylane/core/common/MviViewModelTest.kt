package com.kllxx.memorylane.core.common

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {

    private data class State(val count: Int = 0, val error: UiError? = null)
    private sealed interface Intent { data object Add : Intent }
    private sealed interface Event { data class Added(val by: Int) : Event }
    private sealed interface Effect { data class Done(val n: Int) : Effect }

    private class TestViewModel(errorHandler: ErrorHandler) :
        MviViewModel<State, Intent, Event, Effect>(
            initialState = State(),
            reducer = { state, event -> if (event is Event.Added) state.copy(count = state.count + event.by) else state },
            errorHandler = errorHandler,
        ) {
        override fun onIntent(intent: Intent) {
            update { if (intent == Intent.Add) it.copy(count = it.count + 1) else it }
        }

        fun add(by: Int) = reduce(Event.Added(by))
        fun emit(n: Int) = sendEffect(Effect.Done(n))
        fun current() = currentState

        fun launch(what: String, block: suspend () -> Unit): Job = launchSafe(
            what = what,
            onError = { update { s -> s.copy(error = it) } },
        ) { block() }

        fun <T> catching(source: Flow<T>, fallback: T): Flow<T> =
            source.catchSafe("read the source") { emit(fallback) }

        suspend fun <T> safely(what: String, fallback: T, block: suspend () -> T): T =
            runSafe(what, { fallback }, block)
    }

    private val dispatcher = StandardTestDispatcher()
    private val errorHandler = mockk<ErrorHandler>()
    private val handled = UiError(message = 42)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { errorHandler.handle(any(), any()) } returns handled
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an intent is applied through update and an event through the reducer`() {
        val vm = TestViewModel(errorHandler)

        vm.onIntent(Intent.Add)
        vm.add(by = 5)

        assertEquals(6, vm.state.value.count)
        assertEquals(6, vm.current().count)
    }

    @Test
    fun `an effect is delivered once`() = runTest(dispatcher) {
        val vm = TestViewModel(errorHandler)
        vm.emit(1)

        vm.effects.test {
            assertEquals(Effect.Done(1), awaitItem())
            expectNoEvents()
        }
        // A second collector does not see it again
        vm.effects.test { expectNoEvents() }
    }

    @Test
    fun `an error reaches the handler with what failed and then onError`() = runTest(dispatcher) {
        val vm = TestViewModel(errorHandler)
        val boom = IllegalStateException("boom")

        vm.launch("load the streak") { throw boom }
        advanceUntilIdle()

        verify(exactly = 1) { errorHandler.handle(boom, "load the streak") }
        assertEquals(handled, vm.state.value.error)
    }

    @Test
    fun `a cancellation is rethrown and emits nothing`() = runTest(dispatcher) {
        val vm = TestViewModel(errorHandler)
        val started = CompletableDeferred<Unit>()

        val job = vm.launch("never fails") {
            started.complete(Unit)
            delay(Long.MAX_VALUE)
        }
        advanceUntilIdle()
        assertTrue(started.isCompleted)
        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        verify(exactly = 0) { errorHandler.handle(any(), any()) }
        assertEquals(State(), vm.state.value)
        vm.effects.test { expectNoEvents() }
    }

    @Test
    fun `a cancellation inside runSafe is not turned into the fallback`() = runTest(dispatcher) {
        val vm = TestViewModel(errorHandler)
        var result: String? = null

        val job = vm.launch("step") { result = vm.safely("step", "fallback") { delay(Long.MAX_VALUE); "done" } }
        advanceUntilIdle()
        job.cancel()
        advanceUntilIdle()

        assertEquals(null, result)
        verify(exactly = 0) { errorHandler.handle(any(), any()) }
    }

    @Test
    fun `runSafe returns the result, or the fallback after the error was handled`() = runTest(dispatcher) {
        val vm = TestViewModel(errorHandler)
        val boom = RuntimeException("boom")
        var ok: String? = null
        var failed: String? = null

        vm.launch("steps") {
            ok = vm.safely("fine", "fallback") { "value" }
            failed = vm.safely("broken", "fallback") { throw boom }
        }
        advanceUntilIdle()

        assertEquals("value", ok)
        assertEquals("fallback", failed)
        verify(exactly = 1) { errorHandler.handle(boom, "broken") }
    }

    @Test
    fun `catchSafe logs an upstream failure and lets the fallback emit`() = runTest(dispatcher) {
        val vm = TestViewModel(errorHandler)
        val boom = IllegalStateException("boom")

        vm.catching(flow { emit(1); throw boom }, fallback = 0).test {
            assertEquals(1, awaitItem())
            assertEquals(0, awaitItem())
            awaitComplete()
        }

        verify(exactly = 1) { errorHandler.handle(boom, "read the source") }
    }

    @Test
    fun `catchSafe does not swallow a cancellation`() = runTest(dispatcher) {
        val vm = TestViewModel(errorHandler)

        vm.catching(flow<Int> { throw CancellationException("cancelled") }, fallback = 0).test {
            assertEquals(CancellationException::class, awaitError()::class)
        }

        verify(exactly = 0) { errorHandler.handle(any(), any()) }
    }
}
