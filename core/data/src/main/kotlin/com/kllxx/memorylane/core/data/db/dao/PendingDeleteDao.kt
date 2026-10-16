package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kllxx.memorylane.core.data.db.entity.PendingDeleteEntity
import java.time.LocalDate

@Dao
interface PendingDeleteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingDeleteEntity)

    @Query("SELECT * FROM pending_deletes")
    suspend fun getAll(): List<PendingDeleteEntity>

    @Query("DELETE FROM pending_deletes WHERE photoId = :photoId")
    suspend fun deleteByPhotoId(photoId: Long)

    @Query("DELETE FROM pending_deletes")
    suspend fun clear()

    @Query("DELETE FROM pending_deletes WHERE sessionDate = :date")
    suspend fun clearByDate(date: LocalDate)
}
