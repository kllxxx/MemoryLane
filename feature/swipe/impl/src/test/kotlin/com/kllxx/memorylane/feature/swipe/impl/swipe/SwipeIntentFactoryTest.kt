package com.kllxx.memorylane.feature.swipe.impl.swipe

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SwipeIntentFactoryTest {

    private val uri = Uri.parse("content://media/external/images/media/42")

    @Suppress("DEPRECATION")
    private fun Intent.stream(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)

    @Test
    fun `share is a chooser with the title`() {
        val chooser = SwipeIntentFactory.share(uri, "Поделиться фото")

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Поделиться фото", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))
    }

    @Suppress("DEPRECATION")
    private fun Intent.target(): Intent = getParcelableExtra(Intent.EXTRA_INTENT)!!

    @Test
    fun `the shared intent sends the content uri as an image`() {
        val send = SwipeIntentFactory.share(uri, "title").target()

        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/*", send.type)
        assertEquals(uri, send.stream())
    }

    @Test
    fun `the receiving app is given read access to the photo`() {
        val chooser = SwipeIntentFactory.share(uri, "title")

        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(chooser.target().flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `view opens the content uri as an image`() {
        val view = SwipeIntentFactory.view(uri)

        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(uri, view.data)
        assertEquals("image/*", view.type)
    }

    @Test
    fun `the viewer is given read access to the photo`() {
        assertTrue(SwipeIntentFactory.view(uri).flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
