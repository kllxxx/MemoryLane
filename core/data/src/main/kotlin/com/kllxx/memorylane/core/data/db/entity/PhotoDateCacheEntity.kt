package com.kllxx.memorylane.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The date found for a photo that has no MediaStore `DATE_TAKEN`; a cache that can be rebuilt from the files. */
@Entity(tableName = "photo_date_cache")
data class PhotoDateCacheEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "taken_local_epoch_second") val takenLocalEpochSecond: Long?,
    @ColumnInfo(name = "offset_seconds") val offsetSeconds: Int?,
    val source: String,
    // The default must match MIGRATION_6_7 or Room rejects the migrated schema
    @ColumnInfo(name = "has_time", defaultValue = "1") val hasTime: Boolean = true,
)
