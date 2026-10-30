package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kllxx.memorylane.core.data.db.entity.PhotoLocationCacheEntity

@Dao
interface PhotoLocationCacheDao {
    @Query("SELECT * FROM photo_locations WHERE photoId IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PhotoLocationCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PhotoLocationCacheEntity>)

    @Query("SELECT photoId FROM photo_locations")
    suspend fun allIds(): List<Long>

    @Query("DELETE FROM photo_locations WHERE photoId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM photo_locations")
    suspend fun deleteAll()
}
