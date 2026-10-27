package com.kllxx.memorylane.core.photos.data.repository

import com.kllxx.memorylane.core.photos.data.date.ExifDateReader
import com.kllxx.memorylane.core.photos.data.date.ExifDateTags
import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoDate
import com.kllxx.memorylane.core.photos.domain.repository.PhotoDateCache
import java.io.FileNotFoundException

/** Answers with the tags set for an id and fails like an unreadable file for any other; remembers which files were opened. */
internal class FakeExifDateReader : ExifDateReader {
    val tags = HashMap<Long, ExifDateTags>()
    val reads = mutableListOf<Long>()

    override fun read(photoId: Long): ExifDateTags {
        reads += photoId
        return tags[photoId] ?: throw FileNotFoundException("no file for $photoId")
    }
}

/** An in-memory [PhotoDateCache]. */
internal class FakePhotoDateCache : PhotoDateCache {
    val entries = HashMap<Long, CachedPhotoDate>()
    var writes = 0

    override suspend fun get(ids: Collection<Long>): Map<Long, CachedPhotoDate> =
        ids.mapNotNull { entries[it] }.associateBy { it.id }

    override suspend fun putAll(entries: Collection<CachedPhotoDate>) {
        writes += entries.size
        entries.forEach { this.entries[it.id] = it }
    }

    override suspend fun retainOnly(ids: Set<Long>) {
        entries.keys.retainAll(ids)
    }
}
