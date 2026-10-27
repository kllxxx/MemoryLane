package com.kllxx.memorylane.core.photos.domain.repository

import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoDate

/** Persistent memory of the dates found for photos without a MediaStore `DATE_TAKEN`, keyed by the MediaStore id. */
interface PhotoDateCache {
    /** The cached entries among [ids]; ids that were never cached are missing from the result. */
    suspend fun get(ids: Collection<Long>): Map<Long, CachedPhotoDate>

    /** Adds the entries, replacing those with the same id. */
    suspend fun putAll(entries: Collection<CachedPhotoDate>)

    /** Removes every entry whose id is not in [ids], i.e. photos that were deleted or got a date of their own. */
    suspend fun retainOnly(ids: Set<Long>)
}
