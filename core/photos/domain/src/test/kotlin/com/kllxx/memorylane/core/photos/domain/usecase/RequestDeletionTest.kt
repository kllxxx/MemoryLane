package com.kllxx.memorylane.core.photos.domain.usecase

import android.content.IntentSender
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RequestDeletionTest {

    private val sender = mockk<IntentSender>()
    private val photoRepository = mockk<PhotoRepository> {
        coEvery { getPhotoSizes(any()) } returns mapOf(1L to 100L, 2L to 200L)
        coEvery { deletePhotos(any()) } returns DeleteResult.RequiresConfirmation(sender)
    }

    private val request = RequestDeletion(photoRepository)

    @Test
    fun `the sizes are read before the request and the dialog is asked for`() = runTest {
        val result = request(linkedMapOf(1L to 1L, 2L to 2L))

        assertEquals(DeletionRequest.NeedsConfirmation(mapOf(1L to 100L, 2L to 200L), sender), result)
        coVerifyOrder {
            photoRepository.getPhotoSizes(listOf(1L, 2L))
            photoRepository.deletePhotos(listOf(1L, 2L))
        }
    }

    @Test
    fun `a photo MediaStore has no size for keeps the size the screen knew`() = runTest {
        val result = request(linkedMapOf(1L to 1L, 3L to 333L))

        assertEquals(mapOf(1L to 100L, 3L to 333L), result.bytesById)
    }

    @Test
    fun `photos that are deleted at once are done`() = runTest {
        coEvery { photoRepository.deletePhotos(any()) } returns DeleteResult.Deleted

        assertEquals(DeletionRequest.Done(mapOf(1L to 100L)), request(mapOf(1L to 1L)))
    }

    @Test
    fun `a failure of the request is thrown`() = runTest {
        coEvery { photoRepository.deletePhotos(any()) } throws SecurityException("no")

        try {
            request(mapOf(1L to 1L))
            fail("expected SecurityException")
        } catch (e: SecurityException) {
            assertEquals("no", e.message)
        }
    }

    @Test
    fun `no request is made when the sizes cannot be read`() = runTest {
        coEvery { photoRepository.getPhotoSizes(any()) } throws IllegalStateException("db")

        try {
            request(mapOf(1L to 1L))
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            coVerify(exactly = 0) { photoRepository.deletePhotos(any()) }
        }
    }
}
