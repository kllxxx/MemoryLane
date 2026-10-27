package com.kllxx.memorylane.core.photos.data.date

import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoDate
import com.kllxx.memorylane.core.photos.domain.model.DateSource
import com.kllxx.memorylane.core.photos.domain.repository.PhotoDateCache
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** What resolving the dates of a set of undated photos found, and how: [dates] has only the photos that got a date. */
internal class UndatedDateResolution(
    val dates: Map<Long, ResolvedDate>,
    val undated: Int,
    val cacheHits: Int,
    val cacheMisses: Int,
) {
    val fromExif: Int get() = dates.values.count { it.source == DateSource.EXIF }
    val fromFileName: Int get() = dates.values.count { it.source == DateSource.FILENAME }
    val stillUndated: Int get() = undated - dates.size
}

/**
 * Gives the photos without a MediaStore date the date from the [PhotoDateCache] or, when the cache has none for that
 * version of the file, from the [PhotoDateResolver] (which opens the file). A file is cached by `(id, DATE_MODIFIED)`,
 * also when nothing was found, so it is read once until it changes.
 */
internal class UndatedPhotoDates(
    private val cache: PhotoDateCache,
    private val resolver: PhotoDateResolver,
) {

    /** Cancelling keeps what was already cached, so the next run continues where this one stopped. */
    suspend fun resolve(photos: List<UndatedPhoto>): UndatedDateResolution {
        val cached = cache.get(photos.map { it.id })
        val dates = HashMap<Long, ResolvedDate>()
        val fresh = ArrayList<CachedPhotoDate>()
        var hits = 0
        var misses = 0
        for (photo in photos) {
            currentCoroutineContext().ensureActive()
            val entry = cached[photo.id]?.takeIf { it.dateModified == photo.dateModified }
            if (entry != null) {
                hits++
                ResolvedDate.fromCached(entry)?.let { dates[photo.id] = it }
                continue
            }
            misses++
            val resolved = resolver.resolve(photo)
            if (resolved != null) dates[photo.id] = resolved
            fresh += resolved?.toCached(photo) ?: ResolvedDate.undatedToCached(photo)
            if (fresh.size >= WRITE_BATCH) {
                cache.putAll(fresh)
                fresh.clear()
            }
        }
        if (fresh.isNotEmpty()) cache.putAll(fresh)
        // Only a finished run knows which ids are gone, or have a date of their own by now
        cache.retainOnly(photos.mapTo(HashSet()) { it.id })
        return UndatedDateResolution(dates, undated = photos.size, cacheHits = hits, cacheMisses = misses)
    }

    private companion object {
        const val WRITE_BATCH = 50
    }
}
