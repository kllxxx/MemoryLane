package com.kllxx.memorylane.core.photos.domain.usecase

import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * A step the screen is complete without, or that must not keep a user who is already leaving: a failure is logged and
 * [fallback] is the answer. Cancellation is not a failure.
 */
internal suspend fun <T> bestEffort(what: String, fallback: T, block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Timber.e(e, "Could not %s", what)
    fallback
}
