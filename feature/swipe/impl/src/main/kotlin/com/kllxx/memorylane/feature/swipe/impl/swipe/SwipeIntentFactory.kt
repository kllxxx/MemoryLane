package com.kllxx.memorylane.feature.swipe.impl.swipe

import android.content.Intent
import android.net.Uri

/** The intents behind "Поделиться" and "В галерее", for a content [Uri] of a photo. */
object SwipeIntentFactory {

    private const val IMAGE_MIME = "image/*"

    /** A chooser to send the photo to another app, which is given read access to it. */
    fun share(uri: Uri, chooserTitle: CharSequence): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = IMAGE_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle).apply {
            // The chooser hands the grant on to the app that is picked
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Opens the photo in an app that views images. */
    fun view(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, IMAGE_MIME)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
