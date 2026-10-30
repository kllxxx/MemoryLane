package com.kllxx.memorylane.core.data.time

import android.content.Intent
import app.cash.turbine.test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private class MutableClock(private var instant: Instant, private var zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    override fun instant(): Instant = instant

    fun advanceMillis(millis: Long) {
        instant = instant.plusMillis(millis)
    }

    fun set(instant: Instant, zone: ZoneId = this.zone) {
        this.instant = instant
        this.zone = zone
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SystemTodayProviderTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T23:00:00Z"))
    private val dateChanges = MutableSharedFlow<Unit>()
    private val provider = SystemTodayProvider(clock, dateChanges)

    @Test
    fun `emits the current date at once`() = runTest {
        provider.today.test {
            assertEquals(LocalDate.of(2026, 10, 15), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `now follows the clock`() {
        assertEquals(LocalDate.of(2026, 10, 15), provider.now())
        clock.set(Instant.parse("2026-10-16T00:00:01Z"))
        assertEquals(LocalDate.of(2026, 10, 16), provider.now())
    }

    @Test
    fun `emits the next day at midnight and not before`() = runTest {
        provider.today.test {
            assertEquals(LocalDate.of(2026, 10, 15), awaitItem())

            val hour = 60 * 60 * 1000L
            clock.advanceMillis(hour - 1)
            advanceTimeBy(hour - 1)
            runCurrent()
            expectNoEvents()

            clock.advanceMillis(1)
            advanceTimeBy(1)
            assertEquals(LocalDate.of(2026, 10, 16), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `goes on across several midnights, New Year included`() = runTest {
        clock.set(Instant.parse("2026-12-31T23:00:00Z"))
        provider.today.test {
            assertEquals(LocalDate.of(2026, 12, 31), awaitItem())

            clock.set(Instant.parse("2027-01-01T00:00:00Z"))
            advanceTimeBy(60 * 60 * 1000L)
            assertEquals(LocalDate.of(2027, 1, 1), awaitItem())

            clock.set(Instant.parse("2027-01-02T00:00:00Z"))
            advanceTimeBy(24 * 60 * 60 * 1000L)
            assertEquals(LocalDate.of(2027, 1, 2), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a change of the date, the time or the zone is followed at once`() = runTest {
        provider.today.test {
            assertEquals(LocalDate.of(2026, 10, 15), awaitItem())

            // The zone moves east: it is already the 16th there
            clock.set(Instant.parse("2026-10-15T23:00:00Z"), ZoneOffset.ofHours(3))
            dateChanges.emit(Unit)
            assertEquals(LocalDate.of(2026, 10, 16), awaitItem())

            // The clock is set back by hand
            clock.set(Instant.parse("2026-10-10T12:00:00Z"))
            dateChanges.emit(Unit)
            assertEquals(LocalDate.of(2026, 10, 10), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the same date is not emitted twice`() = runTest {
        provider.today.test {
            assertEquals(LocalDate.of(2026, 10, 15), awaitItem())
            dateChanges.emit(Unit)
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `waits for the next midnight of the zone, at least a millisecond`() {
        val atMidnight = Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC)
        assertEquals(24 * 60 * 60 * 1000L, millisUntilNextMidnight(atMidnight))
        val inZone = Clock.fixed(Instant.parse("2026-10-15T23:30:00Z"), ZoneOffset.ofHours(1))
        assertEquals(23 * 60 * 60 * 1000L + 30 * 60 * 1000L, millisUntilNextMidnight(inZone))
    }

    @Test
    fun `the system broadcasts of a date change reach the flow`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        dateChangeEvents(context).test {
            runCurrent()
            for (action in listOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)) {
                context.sendBroadcast(Intent(action))
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
