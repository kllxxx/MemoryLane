package com.kllxx.memorylane.core.photos.domain.repository

import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoDate

/** Persistent memory of the dates found for photos without a MediaStore `DATE_TAKEN`, keyed by the MediaStore id. */
interface PhotoDateCache {
    /** The cached entries among [ids]; ids that were never cached are missing from the result. */
    suspend fun get(ids: Collection<Long>): Map<Long, CachedPhotoDate>

    /** Every entry that holds a date, i.e. all but the photos known to have none. */
    suspend fun getAllDated(): List<CachedPhotoDate>

    /** Adds the entries, replacing those with the same id. */
    suspend fun putAll(entries: Collection<CachedPhotoDate>)

    /** Removes every entry whose id is not in [ids], i.e. photos that were deleted or got a date of their own. */
    suspend fun retainOnly(ids: Set<Long>)

    /**
     * Forgets what the date chain only guessed, once for each [chainVersion] of the chain: the photos found to have no
     * date (a newer chain may know a source the older one did not) and the dates taken from a file name (a newer chain
     * may read names more strictly). Dates from EXIF, GPS and XMP are kept. Does nothing when this version already ran.
     */
    suspend fun forgetGuessedBefore(chainVersion: Int)
}
