package com.kllxx.memorylane.core.data.impl.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kllxx.memorylane.core.data.impl.db.entity.PendingDeleteEntity

@Dao
interface PendingDeleteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingDeleteEntity)

    @Query("SELECT * FROM pending_deletes")
    suspend fun getAll(): List<PendingDeleteEntity>

    @Query("DELETE FROM pending_deletes")
    suspend fun clear()
}
