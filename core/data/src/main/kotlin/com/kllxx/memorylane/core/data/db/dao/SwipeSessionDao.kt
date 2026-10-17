package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SwipeSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SwipeSessionEntity)

    @Query("SELECT * FROM swipe_sessions ORDER BY date DESC LIMIT 1")
    suspend fun getLast(): SwipeSessionEntity?

    @Query("SELECT * FROM swipe_sessions WHERE completed = 1 AND completed_at = :date LIMIT 1")
    suspend fun getCompletedOn(date: LocalDate): SwipeSessionEntity?

    @Query("SELECT * FROM swipe_sessions WHERE completed = 1 ORDER BY date DESC")
    fun observeCompleted(): Flow<List<SwipeSessionEntity>>
}
