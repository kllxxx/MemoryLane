package com.kllxx.memorylane.core.photos.data.date

import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoDate
import com.kllxx.memorylane.core.photos.domain.model.DateSource
import com.kllxx.memorylane.core.photos.domain.repository.PhotoDateCache
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * What the cache already knows about the undated photos: the [dates] it holds, and the [misses] whose file has to be
 * read (never read, or changed since). A photo the cache knows to have no date is neither in [dates] nor a miss.
 *
 * A photo whose file changed since it was cached keeps its old date in [dates] until the file is read again, so the
 * index does not lose it meanwhile; its old answer (a date, or null for none) is in [stale].
 */
internal class CacheLookup(
    val dates: Map<Long, ResolvedDate>,
    val misses: List<UndatedPhoto>,
    val stale: Map<Long, ResolvedDate?>,
    val undated: Int,
) {
    val cacheHits: Int get() = undated - misses.size

    /** True when reading the files gave a stale photo another date than the one the index was built with. */
    fun changedBy(resolution: MissResolution): Boolean = stale.any { (id, old) ->
        val new = resolution.dates[id]
        new?.dateTime != old?.dateTime || new?.offset != old?.offset || new?.hasTime != old?.hasTime
    }
}

/** The dates found by reading the files of the cache misses: [dates] has only the photos that got a date. */
internal class MissResolution(
    val dates: Map<Long, ResolvedDate>,
    val read: Int,
) {
    val fromExif: Int get() = dates.values.count { it.source == DateSource.EXIF }
    val fromFileName: Int get() = dates.values.count { it.source == DateSource.FILENAME }
    val stillUndated: Int get() = read - dates.size
}

/**
 * Gives the photos without a MediaStore date a date from the [PhotoDateCache] and, for what the cache does not have,
 * from the [PhotoDateResolver] (which opens the file). A file is cached by `(id, DATE_MODIFIED)`, also when nothing
 * was found, so it is read once until it changes.
 *
 * The two steps are apart because the first is cheap and the second is not: the index can be shown with the cache
 * alone, and the files are read afterwards.
 */
internal class UndatedPhotoDates(
    private val cache: PhotoDateCache,
    private val resolver: PhotoDateResolver,
) {

    /** What the cache knows. Entries of photos that are no longer undated (deleted, or dated by MediaStore now) are dropped. */
    suspend fun lookup(photos: List<UndatedPhoto>): CacheLookup {
        val cached = cache.get(photos.map { it.id })
        val dates = HashMap<Long, ResolvedDate>()
        val misses = ArrayList<UndatedPhoto>()
        val stale = HashMap<Long, ResolvedDate?>()
        for (photo in photos) {
            val entry = cached[photo.id]
            val date = entry?.let(ResolvedDate::fromCached)
            if (date != null) dates[photo.id] = date
            when {
                entry == null -> misses += photo
                entry.dateModified != photo.dateModified -> {
                    misses += photo
                    stale[photo.id] = date
                }
            }
        }
        cache.retainOnly(photos.mapTo(HashSet()) { it.id })
        return CacheLookup(dates, misses, stale, undated = photos.size)
    }

    /**
     * Reads the files of [misses]. What was read is in the cache even when this is cancelled, so work is never lost
     * and the next lookup sees it.
     */
    suspend fun resolveMisses(misses: List<UndatedPhoto>): MissResolution {
        val dates = HashMap<Long, ResolvedDate>()
        val fresh = ArrayList<CachedPhotoDate>()
        try {
            for (photo in misses) {
                currentCoroutineContext().ensureActive()
                val resolved = resolver.resolve(photo)
                if (resolved != null) dates[photo.id] = resolved
                fresh += resolved?.toCached(photo) ?: ResolvedDate.undatedToCached(photo)
                if (fresh.size >= WRITE_BATCH) {
                    cache.putAll(fresh)
                    fresh.clear()
                }
            }
        } finally {
            if (fresh.isNotEmpty()) withContext(NonCancellable) { cache.putAll(fresh) }
        }
        return MissResolution(dates, read = misses.size)
    }

    private companion object {
        const val WRITE_BATCH = 50
    }
}
