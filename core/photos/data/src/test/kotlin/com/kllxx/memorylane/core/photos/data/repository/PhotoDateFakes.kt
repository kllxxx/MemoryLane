package com.kllxx.memorylane.core.photos.data.repository

import com.kllxx.memorylane.core.photos.data.date.ExifDateReader
import com.kllxx.memorylane.core.photos.data.date.ExifDateTags
import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoDate
import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoLocation
import com.kllxx.memorylane.core.photos.domain.repository.PhotoDateCache
import com.kllxx.memorylane.core.photos.domain.repository.PhotoLocationCache
import java.io.FileNotFoundException

/** Answers with the tags set for an id and fails like an unreadable file for any other; remembers which files were opened. */
internal class FakeExifDateReader : ExifDateReader {
    val tags = HashMap<Long, ExifDateTags>()
    val reads = mutableListOf<Long>()

    /** Called after each read, to let a test react to the progress of a pass. */
    var onRead: () -> Unit = {}

    override fun read(photoId: Long): ExifDateTags {
        reads += photoId
        onRead()
        return tags[photoId] ?: throw FileNotFoundException("no file for $photoId")
    }
}

/** An in-memory [PhotoDateCache]. */
internal class FakePhotoDateCache : PhotoDateCache {
    val entries = HashMap<Long, CachedPhotoDate>()
    var writes = 0

    override suspend fun get(ids: Collection<Long>): Map<Long, CachedPhotoDate> =
        ids.mapNotNull { entries[it] }.associateBy { it.id }

    override suspend fun getAllDated(): List<CachedPhotoDate> = entries.values.filter { it.takenLocalEpochSecond != null }

    override suspend fun putAll(entries: Collection<CachedPhotoDate>) {
        writes += entries.size
        entries.forEach { this.entries[it.id] = it }
    }

    override suspend fun retainOnly(ids: Set<Long>) {
        entries.keys.retainAll(ids)
    }
}

/** An in-memory [PhotoLocationCache]; a test can make writes fail. */
internal class FakePhotoLocationCache : PhotoLocationCache {
    val entries = HashMap<Long, CachedPhotoLocation>()
    var failWrites = false

    override suspend fun get(ids: Collection<Long>): Map<Long, CachedPhotoLocation> =
        ids.mapNotNull { entries[it] }.associateBy { it.photoId }

    override suspend fun putAll(entries: Collection<CachedPhotoLocation>) {
        if (failWrites) throw IllegalStateException("disk full")
        entries.forEach { this.entries[it.photoId] = it }
    }

    override suspend fun retainOnly(ids: Set<Long>) {
        entries.keys.retainAll(ids)
    }
}
