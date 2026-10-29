package com.kllxx.memorylane.feature.calendar.impl

import com.kllxx.memorylane.core.common.PerfLog
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
    private val threadName = Thread.currentThread().name
    private val day = LocalDate.of(2026, 10, 15)

    @Before
    fun setUp() {
        PerfLog.setEnabled(true)
        PerfLog.sink = { lines += it }
        CalendarPerf.nanoTime = { nowMs * 1_000_000L }
    }

    @After
    fun tearDown() {
        PerfLog.setEnabled(false)
        CalendarPerf.nanoTime = PerfLog::nanoTime
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
                "[$threadName] +0ms t0: CalendarViewModel created",
                "[$threadName] +250ms route entered (cold)",
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

        assertEquals("[$threadName] +0ms t0: route entered (warm, ViewModel reused)", lines[2])
        assertEquals("[$threadName] +100ms first frame with calendar content drawn", lines[3])
    }

    @Test
    fun `the move from Loading to Content or Empty is logged with the time since t0`() {
        nowMs = 0
        CalendarPerf.onViewModelCreated()
        nowMs = 80
        CalendarPerf.onIndexShown(isEmpty = false, photos = 120, days = 90)
        nowMs = 900
        CalendarPerf.onIndexShown(isEmpty = false, photos = 118, days = 89)

        assertEquals("[$threadName] +80ms Loading -> Content: photos=120 days=90", lines[1])
        assertEquals("[$threadName] +900ms index replaced: photos=118 days=89", lines[2])
    }

    @Test
    fun `an empty gallery is logged as Empty`() {
        CalendarPerf.onViewModelCreated()
        CalendarPerf.onIndexShown(isEmpty = true, photos = 0, days = 0)

        assertTrue(lines.last().endsWith("Loading -> Empty: photos=0 days=0"))
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
        assertEquals(1, lines.count { it.endsWith("all visible thumbnails loaded: images=2") })

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
        assertEquals(1, lines.count { it.endsWith("all visible thumbnails loaded: images=2") })
        job.cancel()
    }

    @Test
    fun `nothing is logged or counted when logging is off`() {
        PerfLog.setEnabled(false)

        CalendarPerf.onViewModelCreated()
        CalendarPerf.onRouteEntered()
        CalendarPerf.onRefreshStarted()
        CalendarPerf.onIndexShown(isEmpty = false, photos = 1, days = 1)
        CalendarPerf.onThumbnail(day, succeeded())

        assertTrue(lines.isEmpty())
    }
}
