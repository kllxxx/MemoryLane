package com.kllxx.memorylane.core.photos.domain.repository

import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoLocation

/** Persistent memory of the places found for photos, keyed by the MediaStore id. */
interface PhotoLocationCache {
    /** The cached entries among [ids]; ids that were never cached are missing from the result. */
    suspend fun get(ids: Collection<Long>): Map<Long, CachedPhotoLocation>

    /** Adds the entries, replacing those with the same id. */
    suspend fun putAll(entries: Collection<CachedPhotoLocation>)

    /** Removes every entry whose id is not in [ids], i.e. photos that were deleted. */
    suspend fun retainOnly(ids: Set<Long>)

    /** Removes every entry, so each place is looked up again. */
    suspend fun clear()
}
