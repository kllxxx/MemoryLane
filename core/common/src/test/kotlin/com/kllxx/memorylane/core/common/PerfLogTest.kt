package com.kllxx.memorylane.core.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerfLogTest {

    private val lines = mutableListOf<String>()

    @Before
    fun setUp() {
        PerfLog.sink = { lines += it }
    }

    @After
    fun tearDown() {
        PerfLog.setEnabled(false)
    }

    @Test
    fun `disabled logging never builds the message`() {
        PerfLog.setEnabled(false)
        var built = false

        PerfLog.d { built = true; "never" }

        assertFalse(built)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `enabled logging adds the thread name`() {
        PerfLog.setEnabled(true)

        PerfLog.d { "hello" }

        assertEquals(listOf("[${Thread.currentThread().name}] hello"), lines)
    }

    @Test
    fun `time is only measured when enabled`() {
        PerfLog.setEnabled(false)
        assertEquals(0L, PerfLog.nanoTime())

        PerfLog.setEnabled(true)
        assertTrue(PerfLog.nanoTime() != 0L)
        assertTrue(PerfLog.sinceMs(PerfLog.nanoTime()) >= 0)
    }
}
