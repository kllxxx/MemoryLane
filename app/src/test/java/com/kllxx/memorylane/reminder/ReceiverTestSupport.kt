package com.kllxx.memorylane.reminder

import android.content.Intent
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import timber.log.Timber

/** Sends [action] to the manifest receiver [receiver] and waits until the work it started in [appScope] is over. */
internal fun deliver(receiver: Class<*>, action: String, appScope: CoroutineScope) {
    val context = RuntimeEnvironment.getApplication()
    context.sendBroadcast(Intent(context, receiver).setAction(action))
    shadowOf(Looper.getMainLooper()).idle()
    // The receivers launch in the application scope and finish their PendingResult at the end of the coroutine
    runBlocking { appScope.coroutineContext[Job]!!.children.toList().joinAll() }
}

/** Collects what Timber is asked to log, so a test can see that an error was logged and not thrown. */
internal class CapturingTree : Timber.Tree() {
    class Entry(val priority: Int, val message: String, val throwable: Throwable?)

    val entries = mutableListOf<Entry>()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Timber appends the stack trace of [t] to the message; the first line is what the code wrote
        synchronized(entries) { entries += Entry(priority, message.lineSequence().first(), t) }
    }

    fun errors(): List<Entry> = synchronized(entries) { entries.filter { it.priority == android.util.Log.ERROR } }
}
