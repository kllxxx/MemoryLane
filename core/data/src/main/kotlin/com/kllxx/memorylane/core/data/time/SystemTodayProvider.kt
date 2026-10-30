package com.kllxx.memorylane.core.data.time

import com.kllxx.memorylane.core.domain.time.TodayProvider
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

/**
 * [TodayProvider] over the injected [Clock]. [dateChanges] emits when the system date, time or zone is changed; each
 * of them starts the wait for the next midnight again, because the old wait was worked out for the old time.
 */
class SystemTodayProvider(
    private val clock: Clock,
    dateChanges: Flow<Unit>,
) : TodayProvider {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val today: Flow<LocalDate> = dateChanges
        .onStart { emit(Unit) }
        .transformLatest {
            while (true) {
                emit(now())
                delay(millisUntilNextMidnight(clock))
            }
        }
        .distinctUntilChanged()

    override fun now(): LocalDate = LocalDate.now(clock)
}

/** How long until the next local midnight on [clock], at least one millisecond. */
internal fun millisUntilNextMidnight(clock: Clock): Long {
    val now = clock.instant().atZone(clock.zone)
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay(clock.zone)
    return Duration.between(now, midnight).toMillis().coerceAtLeast(1)
}
