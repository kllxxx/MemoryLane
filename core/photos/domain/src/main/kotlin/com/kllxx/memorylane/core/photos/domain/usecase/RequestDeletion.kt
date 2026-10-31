package com.kllxx.memorylane.core.photos.domain.usecase

import android.content.IntentSender
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import javax.inject.Inject

/** What asking the system to delete the photos led to. [bytesById] is the size of every photo, read before the request. */
sealed interface DeletionRequest {
    val bytesById: Map<Long, Long>

    /** The user has to confirm in the system dialog that [intentSender] opens. */
    data class NeedsConfirmation(override val bytesById: Map<Long, Long>, val intentSender: IntentSender) : DeletionRequest

    /** The photos are handled already. */
    data class Done(override val bytesById: Map<Long, Long>) : DeletionRequest
}

/**
 * Reads the sizes of the photos and then asks to delete them: the sizes first, because afterwards trashed photos are no
 * longer in a normal query. Failures are thrown to the caller.
 */
class RequestDeletion @Inject constructor(
    private val photoRepository: PhotoRepository,
) {

    /** [photos]: id to the size known on the screen, which stands in for a size MediaStore does not have. */
    suspend operator fun invoke(photos: Map<Long, Long>): DeletionRequest {
        val read = photoRepository.getPhotoSizes(photos.keys.toList())
        val bytesById = photos.mapValues { (id, known) -> read[id] ?: known }
        return when (val result = photoRepository.deletePhotos(bytesById.keys.toList())) {
            is DeleteResult.RequiresConfirmation -> DeletionRequest.NeedsConfirmation(bytesById, result.intentSender)
            DeleteResult.Deleted -> DeletionRequest.Done(bytesById)
        }
    }
}
