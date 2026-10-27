package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.data.db.dao.PhotoDateCacheDao
import com.kllxx.memorylane.core.data.db.entity.PhotoDateCacheEntity
import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoDate
import com.kllxx.memorylane.core.photos.domain.model.DateSource
import com.kllxx.memorylane.core.photos.domain.repository.PhotoDateCache
import javax.inject.Inject

class PhotoDateCacheImpl @Inject constructor(
    private val dao: PhotoDateCacheDao,
) : PhotoDateCache {

    override suspend fun get(ids: Collection<Long>): Map<Long, CachedPhotoDate> =
        ids.chunked(SQL_VARIABLE_LIMIT)
            .flatMap { dao.getByIds(it) }
            .associate { it.id to it.toDomain() }

    override suspend fun getAllDated(): List<CachedPhotoDate> = dao.getAllDated().map { it.toDomain() }

    override suspend fun putAll(entries: Collection<CachedPhotoDate>) {
        entries.chunked(WRITE_CHUNK).forEach { chunk -> dao.upsertAll(chunk.map { it.toEntity() }) }
    }

    override suspend fun retainOnly(ids: Set<Long>) {
        dao.allIds().filterNot { it in ids }
            .chunked(SQL_VARIABLE_LIMIT)
            .forEach { dao.deleteByIds(it) }
    }

    private companion object {
        // SQLite allows 999 variables per statement on older Android versions
        const val SQL_VARIABLE_LIMIT = 500
        const val WRITE_CHUNK = 500
    }
}

internal fun CachedPhotoDate.toEntity() = PhotoDateCacheEntity(
    id = id,
    dateModified = dateModified,
    takenLocalEpochSecond = takenLocalEpochSecond,
    offsetSeconds = offsetSeconds,
    source = source.name,
)

internal fun PhotoDateCacheEntity.toDomain() = CachedPhotoDate(
    id = id,
    dateModified = dateModified,
    takenLocalEpochSecond = takenLocalEpochSecond,
    offsetSeconds = offsetSeconds,
    // A value written by a newer version of the app reads as undated and is simply resolved again
    source = DateSource.entries.firstOrNull { it.name == source } ?: DateSource.UNDATED,
)
