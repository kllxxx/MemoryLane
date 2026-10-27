package com.kllxx.memorylane.core.photos.data.date

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject

/** Reads the EXIF date tags from the file behind a MediaStore image; this is the only place the date fallback opens files. */
internal class MediaStoreExifDateReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExifDateReader {

    override fun read(photoId: Long): ExifDateTags {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId)
        val stream = open(uri) ?: throw FileNotFoundException("No file behind $uri")
        return stream.use(::readExifDateTags)
    }

    /**
     * The original file when the app may see its location data (the redacted copy still has the date tags,
     * so this only matters for the GPS tags in the diagnostics); the plain copy otherwise.
     */
    private fun open(uri: android.net.Uri): InputStream? {
        val resolver = context.contentResolver
        val mayReadOriginal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (mayReadOriginal) {
            try {
                return resolver.openInputStream(MediaStore.setRequireOriginal(uri))
            } catch (e: Exception) {
                // Fall through to the plain copy
            }
        }
        return resolver.openInputStream(uri)
    }
}
