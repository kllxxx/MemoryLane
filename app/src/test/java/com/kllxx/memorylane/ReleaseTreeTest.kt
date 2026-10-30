package com.kllxx.memorylane

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
class ReleaseTreeTest {

    private val tree = ReleaseTree()

    @Before
    fun setUp() {
        ShadowLog.clear()
        // The application of the test run plants a DebugTree of its own
        Timber.uprootAll()
        Timber.plant(tree)
    }

    @After
    fun tearDown() = Timber.uprootAll()

    @Test
    fun `debug and info never reach Logcat, warnings and errors do`() {
        Timber.d("debug")
        Timber.i("info")
        Timber.w("warning")
        Timber.e(IllegalStateException("boom"), "Could not do it")

        val logs = ShadowLog.getLogs()
        assertEquals(listOf(Log.WARN, Log.ERROR), logs.map { it.type })
        assertTrue(logs.last().msg.startsWith("Could not do it"))
        assertTrue(logs.last().msg.contains("IllegalStateException: boom"))
    }
}
