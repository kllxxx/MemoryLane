package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kllxx.memorylane.core.data.db.entity.PhotoDateCacheEntity

@Dao
interface PhotoDateCacheDao {
    @Query("SELECT * FROM photo_date_cache WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PhotoDateCacheEntity>

    @Query("SELECT * FROM photo_date_cache WHERE taken_local_epoch_second IS NOT NULL")
    suspend fun getAllDated(): List<PhotoDateCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PhotoDateCacheEntity>)

    @Query("SELECT id FROM photo_date_cache")
    suspend fun allIds(): List<Long>

    @Query("DELETE FROM photo_date_cache WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM photo_date_cache WHERE source IN (:sources)")
    suspend fun deleteBySources(sources: List<String>)
}
