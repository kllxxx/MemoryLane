package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.data.db.dao.PhotoLocationCacheDao
import com.kllxx.memorylane.core.data.db.entity.PhotoLocationCacheEntity
import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoLocation
import com.kllxx.memorylane.core.photos.domain.repository.PhotoLocationCache
import javax.inject.Inject

class PhotoLocationCacheImpl @Inject constructor(
    private val dao: PhotoLocationCacheDao,
) : PhotoLocationCache {

    override suspend fun get(ids: Collection<Long>): Map<Long, CachedPhotoLocation> =
        ids.chunked(SQL_VARIABLE_LIMIT)
            .flatMap { dao.getByIds(it) }
            .associate { it.photoId to it.toDomain() }

    override suspend fun putAll(entries: Collection<CachedPhotoLocation>) {
        entries.chunked(WRITE_CHUNK).forEach { chunk -> dao.upsertAll(chunk.map { it.toEntity() }) }
    }

    override suspend fun retainOnly(ids: Set<Long>) {
        dao.allIds().filterNot { it in ids }
            .chunked(SQL_VARIABLE_LIMIT)
            .forEach { dao.deleteByIds(it) }
    }

    override suspend fun clear() = dao.deleteAll()

    private companion object {
        // SQLite allows 999 variables per statement on older Android versions
        const val SQL_VARIABLE_LIMIT = 500
        const val WRITE_CHUNK = 500
    }
}

internal fun CachedPhotoLocation.toEntity() = PhotoLocationCacheEntity(
    photoId = photoId,
    dateModified = dateModified,
    locale = locale,
    location = location,
)

internal fun PhotoLocationCacheEntity.toDomain() = CachedPhotoLocation(
    photoId = photoId,
    dateModified = dateModified,
    locale = locale,
    location = location,
)
