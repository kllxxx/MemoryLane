package com.kllxx.memorylane.core.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class TimberErrorHandlerTest {

    private class Logged(val priority: Int, val message: String, val throwable: Throwable?)

    private val logged = mutableListOf<Logged>()
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logged += Logged(priority, message, t)
        }
    }

    @Before
    fun setUp() = Timber.plant(tree)

    @After
    fun tearDown() = Timber.uproot(tree)

    @Test
    fun `a failure is logged as an error with its cause and what failed`() {
        val boom = IllegalStateException("boom")

        TimberErrorHandler().handle(boom, "load the streak")

        assertEquals(1, logged.size)
        assertEquals(android.util.Log.ERROR, logged.single().priority)
        // Timber appends the stack trace to the message of a tree
        assertTrue(logged.single().message.startsWith("Could not load the streak"))
        assertSame(boom, logged.single().throwable)
    }

    @Test
    fun `any failure maps to the generic error`() {
        assertEquals(UiError.Generic, TimberErrorHandler().handle(RuntimeException(), "do it"))
    }
}
