package com.kllxx.memorylane.core.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Intent → ViewModel → State → UI, with one-shot [Ef]fects on the side. The state only changes through the reducer.
 *
 * Failures: [launchSafe] and [runSafe] rethrow cancellation and hand every other exception to the [ErrorHandler].
 * A load failure goes to the State (with a retry intent), an action failure to a `ShowError` effect, and a
 * best-effort one is only logged and falls back.
 */
abstract class MviViewModel<S : Any, I : Any, Ev : Any, Ef : Any>(
    initialState: S,
    private val reducer: (state: S, event: Ev) -> S,
    private val errorHandler: ErrorHandler,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<Ef>(Channel.BUFFERED)
    val effects: Flow<Ef> = _effects.receiveAsFlow()

    abstract fun onIntent(intent: I)

    protected val currentState: S
        get() = _state.value

    protected fun reduce(event: Ev) {
        _state.update { reducer(it, event) }
    }

    /**
     * Only for applying an existing reducer to an intent: `update { FooReducer.reduce(it, intent) }`.
     * The state is never changed by hand through it.
     */
    protected fun update(transform: (S) -> S) {
        _state.update(transform)
    }

    protected fun sendEffect(effect: Ef) {
        _effects.trySend(effect)
    }

    /** [what] completes "Could not …" in the log. [onError] runs for a failure, never for a cancellation. */
    protected fun launchSafe(
        what: String,
        onError: (UiError) -> Unit = {},
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(errorHandler.handle(e, what))
        }
    }

    /** The same for a step in the middle of a coroutine: a failure is logged and [fallback] stands in for the result. */
    protected suspend fun <T> runSafe(
        what: String,
        fallback: (UiError) -> T,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback(errorHandler.handle(e, what))
    }

    /** For a Flow operator chain: a failure upstream is logged, and [fallback] may emit a stand-in value or rethrow. */
    protected fun <T> Flow<T>.catchSafe(
        what: String,
        fallback: suspend FlowCollector<T>.(UiError) -> Unit,
    ): Flow<T> = catch { e ->
        // Only failures are handled: neither a cancellation nor an Error is ours to swallow
        if (e is CancellationException || e !is Exception) throw e
        fallback(errorHandler.handle(e, what))
    }
}
