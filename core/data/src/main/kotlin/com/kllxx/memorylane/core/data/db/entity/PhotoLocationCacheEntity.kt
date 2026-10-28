package com.kllxx.memorylane.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The place found for a photo; a cache that can be rebuilt. A null [location] means "no place", not "not looked up". */
@Entity(tableName = "photo_locations")
data class PhotoLocationCacheEntity(
    @PrimaryKey val photoId: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    val locale: String,
    val location: String?,
)
