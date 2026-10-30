package com.kllxx.memorylane

import com.kllxx.memorylane.core.domain.time.TodayProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A clock a test moves, to see what happens across midnight. */
class MutableClock(private var instant: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    override fun instant(): Instant = instant

    fun set(instant: Instant) {
        this.instant = instant
    }
}

/** [TodayProvider] over a [MutableClock]: [now] always follows the clock, [today] emits when [tick] is called. */
class ClockTodayProvider(private val clock: Clock) : TodayProvider {
    private val current = MutableStateFlow(now())
    override val today: Flow<java.time.LocalDate> = current
    override fun now(): java.time.LocalDate = java.time.LocalDate.now(clock)
    fun tick() {
        current.value = now()
    }
}
