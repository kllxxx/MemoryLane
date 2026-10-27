package com.kllxx.memorylane.feature.swipe.impl.calendar

import com.kllxx.memorylane.core.ui.component.ThumbnailLoad
import com.kllxx.memorylane.core.ui.component.ThumbnailPhase
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarPerfTest {

    private val lines = mutableListOf<String>()
    private var nowMs = 0L
    private val day = LocalDate.of(2026, 10, 15)

    @Before
    fun setUp() {
        CalendarPerf.sink = { lines += it }
        CalendarPerf.nanoTime = { nowMs * 1_000_000L }
        CalendarPerf.threadName = { "main" }
    }

    @After
    fun tearDown() {
        CalendarPerf.sink = { }
    }

    private fun started() = ThumbnailLoad(ThumbnailPhase.Started)

    private fun succeeded() = ThumbnailLoad(
        ThumbnailPhase.Success,
        durationMs = 40,
        source = "DISK",
        bitmapWidth = 540,
        bitmapHeight = 594,
        cellWidthPx = 360,
        cellHeightPx = 396,
    )

    @Test
    fun `every line has the thread name and the time since t0`() {
        nowMs = 1_000
        CalendarPerf.onViewModelCreated()
        nowMs = 1_250
        CalendarPerf.onRouteEntered()

        assertEquals(
            listOf(
                "[main] +0ms t0: CalendarViewModel created",
                "[main] +250ms route entered (cold)",
            ),
            lines,
        )
    }

    @Test
    fun `a reused ViewModel makes the route entry a new t0`() {
        nowMs = 0
        CalendarPerf.onViewModelCreated()
        CalendarPerf.onRouteEntered()
        nowMs = 5_000
        CalendarPerf.onRouteEntered()
        nowMs = 5_100
        CalendarPerf.onFirstContentFrame()

        assertEquals("[main] +0ms t0: route entered (warm, ViewModel reused)", lines[2])
        assertEquals("[main] +100ms first frame with calendar content drawn", lines[3])
    }

    @Test
    fun `the first state with data is logged once, with the batch and the query count`() {
        CalendarPerf.onViewModelCreated()
        repeat(3) { CalendarPerf.onQuery() }
        CalendarPerf.onDaysLoaded(days = 3, batchMs = 12, anyPhotos = false)
        CalendarPerf.onDaysLoaded(days = 3, batchMs = 15, anyPhotos = true)
        CalendarPerf.onDaysLoaded(days = 3, batchMs = 9, anyPhotos = true)

        assertEquals(1, lines.count { it.endsWith("first state with calendar data emitted") })
        assertTrue(lines.any { it.endsWith("loadDays: 3 days in 15ms, queries so far=3") })
    }

    @Test
    fun `a thumbnail result is logged with its source and the bitmap against the cell size`() {
        CalendarPerf.onViewModelCreated()
        CalendarPerf.onThumbnail(day, started())
        CalendarPerf.onThumbnail(day, succeeded())

        assertTrue(lines.last().endsWith("thumb ok 2026-10-15 40ms source=DISK bitmap=540x594 cell=360x396"))
    }

    @Test
    fun `the summary is logged once, after the frame and when no thumbnail is pending`() = runTest {
        CalendarPerf.onViewModelCreated()
        val job = launch { CalendarPerf.logSummaryWhenThumbnailsLoaded() }

        CalendarPerf.onThumbnail(day, started())
        CalendarPerf.onThumbnail(day.plusDays(1), started())
        CalendarPerf.onFirstContentFrame()
        CalendarPerf.onThumbnail(day, succeeded())
        runCurrent()
        advanceTimeBy(1_000)
        assertTrue("one thumbnail is still pending", lines.none { it.contains("all visible thumbnails") })

        CalendarPerf.onThumbnail(day.plusDays(1), succeeded())
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, lines.count { it.endsWith("all visible thumbnails loaded: images=2, queries=0") })

        // Scrolling in more thumbnails later does not log a second summary
        CalendarPerf.onThumbnail(day.plusDays(2), started())
        CalendarPerf.onThumbnail(day.plusDays(2), succeeded())
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, lines.count { it.contains("all visible thumbnails") })
        job.cancel()
    }

    @Test
    fun `a cancelled or failed thumbnail no longer counts as pending`() = runTest {
        CalendarPerf.onViewModelCreated()
        val job = launch { CalendarPerf.logSummaryWhenThumbnailsLoaded() }

        CalendarPerf.onThumbnail(day, started())
        CalendarPerf.onThumbnail(day.plusDays(1), started())
        CalendarPerf.onFirstContentFrame()
        CalendarPerf.onThumbnail(day, ThumbnailLoad(ThumbnailPhase.Cancelled, durationMs = 5))
        CalendarPerf.onThumbnail(day.plusDays(1), ThumbnailLoad(ThumbnailPhase.Error, durationMs = 7, error = "boom"))
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(lines.any { it.endsWith("thumb cancelled 2026-10-15 5ms") })
        assertTrue(lines.any { it.endsWith("thumb FAILED 2026-10-16 7ms boom") })
        assertEquals(1, lines.count { it.endsWith("all visible thumbnails loaded: images=2, queries=0") })
        job.cancel()
    }
}
