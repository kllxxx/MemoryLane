package com.kllxx.memorylane.core.photos.data.date

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime

/**
 * [MediaStoreExifDateReader] against real JPEG bytes with EXIF, written in the test by the same ExifInterface the
 * reader uses, and served through the content resolver of Robolectric. The reader is the only code that opens a
 * photo's file; everything above it is tested with a fake reader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaStoreExifDateReaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val resolver get() = shadowOf(context.contentResolver)
    private val reader = MediaStoreExifDateReader(context)

    private val photoId = 42L
    private val plainUri: Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId)
    // Lazy: setRequireOriginal does not exist before Android 10
    private val originalUri: Uri by lazy { MediaStore.setRequireOriginal(plainUri) }

    @Before
    fun setUp() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    /** A real 2x2 JPEG with the given EXIF tags. */
    private fun jpeg(vararg tags: Pair<String, String>): ByteArray {
        val file = File(folder.root, "photo-${System.nanoTime()}.jpg")
        file.outputStream().use { out ->
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        if (tags.isNotEmpty()) {
            val exif = ExifInterface(file.absolutePath)
            tags.forEach { (tag, value) -> exif.setAttribute(tag, value) }
            exif.saveAttributes()
        }
        return file.readBytes()
    }

    private fun serve(uri: Uri, bytes: ByteArray) = resolver.registerInputStream(uri, ByteArrayInputStream(bytes))

    private fun allowOriginalFiles() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    // --- what is read ---

    @Test
    fun `the three date tags, their offsets and the GPS date stamp are read from the file`() {
        serve(
            plainUri,
            jpeg(
                ExifInterface.TAG_DATETIME_ORIGINAL to "2016:01:07 10:15:00",
                ExifInterface.TAG_DATETIME_DIGITIZED to "2016:01:08 11:16:01",
                ExifInterface.TAG_DATETIME to "2016:01:09 12:17:02",
                ExifInterface.TAG_OFFSET_TIME_ORIGINAL to "+03:00",
                ExifInterface.TAG_OFFSET_TIME_DIGITIZED to "+04:00",
                ExifInterface.TAG_OFFSET_TIME to "+05:00",
                ExifInterface.TAG_GPS_DATESTAMP to "2016:01:07",
            ),
        )

        val tags = reader.read(photoId)

        assertEquals("2016:01:07 10:15:00", tags.dateTimeOriginal)
        assertEquals("2016:01:08 11:16:01", tags.dateTimeDigitized)
        assertEquals("2016:01:09 12:17:02", tags.dateTime)
        assertEquals("+03:00", tags.offsetTimeOriginal)
        assertEquals("+04:00", tags.offsetTimeDigitized)
        assertEquals("+05:00", tags.offsetTime)
        assertEquals("2016:01:07", tags.gpsDate)
        // The GPS time and the XMP packet are not covered: ExifInterface does not store them from a plain setAttribute
    }

    @Test
    fun `the candidates of the file are in the trusted order`() {
        serve(
            plainUri,
            jpeg(
                ExifInterface.TAG_DATETIME_ORIGINAL to "2016:01:07 10:15:00",
                ExifInterface.TAG_OFFSET_TIME_ORIGINAL to "+03:00",
            ),
        )

        val first = reader.read(photoId).candidates().first()

        assertEquals("DateTimeOriginal", first.tag)
        assertEquals(LocalDateTime.of(2016, 1, 7, 10, 15, 0), first.parse()?.first)
    }

    @Test
    fun `a file without date tags gives empty tags`() {
        serve(plainUri, jpeg())

        val tags = reader.read(photoId)

        assertNull(tags.dateTimeOriginal)
        assertNull(tags.dateTimeDigitized)
        assertNull(tags.dateTime)
        assertNull(tags.gpsDate)
        assertTrue(tags.candidates().all { it.parse() == null })
    }

    @Test
    fun `bytes that are not an image give empty tags instead of a crash`() {
        serve(plainUri, "not a picture".toByteArray())

        val tags = reader.read(photoId)

        assertNull(tags.dateTimeOriginal)
        assertNull(tags.dateTime)
    }

    @Test
    fun `a photo with no file behind it fails with a file not found`() {
        resolver.registerInputStreamSupplier(plainUri) { null }

        try {
            reader.read(photoId)
            fail("expected a FileNotFoundException")
        } catch (e: FileNotFoundException) {
            assertTrue(e.message.orEmpty().contains(plainUri.toString()))
        }
    }

    // --- the original file ---

    @Test
    fun `with the location permission the original file is read`() {
        allowOriginalFiles()
        serve(plainUri, jpeg(ExifInterface.TAG_DATETIME_ORIGINAL to "2016:01:07 10:15:00"))
        serve(originalUri, jpeg(ExifInterface.TAG_DATETIME_ORIGINAL to "2020:05:06 07:08:09"))

        assertEquals("2020:05:06 07:08:09", reader.read(photoId).dateTimeOriginal)
    }

    @Test
    fun `without the location permission the plain copy is read`() {
        serve(plainUri, jpeg(ExifInterface.TAG_DATETIME_ORIGINAL to "2016:01:07 10:15:00"))
        serve(originalUri, jpeg(ExifInterface.TAG_DATETIME_ORIGINAL to "2020:05:06 07:08:09"))

        assertEquals("2016:01:07 10:15:00", reader.read(photoId).dateTimeOriginal)
    }

    @Test
    fun `when the original cannot be opened the plain copy is read`() {
        allowOriginalFiles()
        serve(plainUri, jpeg(ExifInterface.TAG_DATETIME_ORIGINAL to "2016:01:07 10:15:00"))
        resolver.registerInputStreamSupplier(originalUri) { throw IOException("the original is gone") }

        assertEquals("2016:01:07 10:15:00", reader.read(photoId).dateTimeOriginal)
    }

    @Test
    @Config(sdk = [28])
    fun `before Android 10 there is no original to ask for`() {
        allowOriginalFiles()
        serve(plainUri, jpeg(ExifInterface.TAG_DATETIME_ORIGINAL to "2016:01:07 10:15:00"))

        assertEquals("2016:01:07 10:15:00", reader.read(photoId).dateTimeOriginal)
    }
}
