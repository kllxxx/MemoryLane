package com.kllxx.memorylane.feature.swipe.impl.swipe

import com.kllxx.memorylane.core.photos.domain.model.Photo

/** Results produced by [SwipeViewModel] itself. The UI never sends these. */
internal sealed interface SwipeEvent {
    data class PhotosLoaded(val photos: List<Photo>) : SwipeEvent
}
