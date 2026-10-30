package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kllxx.memorylane.core.data.db.entity.PhotoDecisionEntity
import com.kllxx.memorylane.core.data.mapper.StoredDeck
import java.time.LocalDate

@Dao
interface PhotoDecisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhotoDecisionEntity)

    @Query("SELECT * FROM photo_decisions")
    suspend fun getAll(): List<PhotoDecisionEntity>

    @Query("SELECT * FROM photo_decisions WHERE sessionDate = :date")
    suspend fun getByDate(date: LocalDate): List<PhotoDecisionEntity>

    @Query("SELECT * FROM photo_decisions WHERE decision = 'DELETE'")
    suspend fun getPendingDeletes(): List<PhotoDecisionEntity>

    @Query("SELECT * FROM photo_decisions WHERE photoId = :photoId")
    suspend fun getById(photoId: Long): PhotoDecisionEntity?

    @Query("SELECT * FROM photo_decisions WHERE photoId IN (:photoIds)")
    suspend fun getByIds(photoIds: List<Long>): List<PhotoDecisionEntity>

    @Query("DELETE FROM photo_decisions WHERE photoId = :photoId")
    suspend fun deleteById(photoId: Long)

    // The photos that got a date move from the undated deck to their day; a decision in any other deck stays where it is
    @Query("UPDATE photo_decisions SET sessionDate = :date WHERE photoId IN (:photoIds) AND sessionDate = ${StoredDeck.UNDATED_EPOCH_DAY}")
    suspend fun moveFromUndated(photoIds: List<Long>, date: LocalDate)

    @Query("UPDATE photo_decisions SET decision = :decision, decided_at = :decidedAt WHERE photoId IN (:photoIds)")
    suspend fun updateDecisions(photoIds: List<Long>, decision: String, decidedAt: Long)
}
