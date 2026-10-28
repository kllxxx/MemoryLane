package com.kllxx.memorylane.core.photos.domain.model

/** The place of one photo as "City, Country", or null when it has no usable location. */
data class PhotoLocation(
    val photoId: Long,
    val location: String?,
)

/**
 * A resolved place that is remembered so a photo is geocoded only once. Valid while the photo's `DATE_MODIFIED`
 * ([dateModified]) and the app language ([locale], a language tag) are unchanged. A null [location] means the
 * photo has no GPS data or the geocoder knows no place for it.
 */
data class CachedPhotoLocation(
    val photoId: Long,
    val dateModified: Long,
    val locale: String,
    val location: String?,
)
