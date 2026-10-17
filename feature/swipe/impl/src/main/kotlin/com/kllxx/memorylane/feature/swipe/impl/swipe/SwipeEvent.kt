package com.kllxx.memorylane.feature.swipe.impl.swipe

import com.kllxx.memorylane.core.photos.domain.model.Photo

/** Results produced by [SwipeViewModel] itself. The UI never sends these. */
internal sealed interface SwipeEvent {
    /** [pendingDeleteIds] are the photos of this date already marked for deletion in an earlier run. */
    data class PhotosLoaded(
        val photos: List<Photo>,
        val pendingDeleteIds: List<Long> = emptyList(),
    ) : SwipeEvent

    /** The place of one photo, looked up when its card became the current one. Null if it has none. */
    data class LocationLoaded(val photoId: Long, val location: String?) : SwipeEvent
}
