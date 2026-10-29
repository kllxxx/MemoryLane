package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kllxx.memorylane.core.data.db.entity.PhotoDecisionEntity
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity
import com.kllxx.memorylane.core.data.mapper.toDomain
import com.kllxx.memorylane.core.domain.model.DeletionOutcome
import com.kllxx.memorylane.core.domain.streak.SessionCompletion
import java.time.LocalDate

/** The one write that has to be atomic: a confirmed deletion changes decisions and the sessions together. */
@Dao
abstract class DeletionDao {

    @Query("SELECT * FROM photo_decisions WHERE photoId IN (:photoIds) AND decision = 'DELETE'")
    abstract suspend fun markedAmong(photoIds: List<Long>): List<PhotoDecisionEntity>

    @Query("UPDATE photo_decisions SET decision = :decision, decided_at = :decidedAt WHERE photoId IN (:photoIds)")
    abstract suspend fun setDecisions(photoIds: List<Long>, decision: String, decidedAt: Long)

    @Query("SELECT * FROM swipe_sessions WHERE date = :date")
    abstract suspend fun getSession(date: LocalDate): SwipeSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putSession(session: SwipeSessionEntity)

    /**
     * Moves the photos of [bytesById] that are still DELETE to [decision], each in its own day, and adds the photos of
     * a day to the session of that day. Photos that are not DELETE any more (already confirmed, or unmarked) are
     * ignored, so a second call with the same photos changes nothing and returns zero: the deletion is counted once.
     * The outcome covers all days.
     */
    @Transaction
    open suspend fun confirmDeletion(
        bytesById: Map<Long, Long>,
        decision: String,
        decidedAt: Long,
        today: LocalDate,
    ): DeletionOutcome {
        val applied = bytesById.keys.toList().chunked(SQL_VARIABLE_LIMIT).flatMap { markedAmong(it) }
        if (applied.isEmpty()) return DeletionOutcome(deletedCount = 0, freedBytes = 0)
        applied.map { it.photoId }.chunked(SQL_VARIABLE_LIMIT).forEach { setDecisions(it, decision, decidedAt) }
        applied.groupBy { it.sessionDate }.forEach { (date, rows) ->
            val freed = rows.sumOf { bytesById.getValue(it.photoId) }
            val existing = getSession(date)
            putSession(
                SwipeSessionEntity(
                    date = date,
                    completed = true,
                    savedBytes = (existing?.savedBytes ?: 0) + freed,
                    // A day completed by its swipes keeps the date it was completed on
                    completedAt = if (existing?.completed == true) {
                        existing.completedAt
                    } else {
                        SessionCompletion.completedAt(existing?.toDomain(), date, today)
                    },
                    deletedCount = (existing?.deletedCount ?: 0) + rows.size,
                ),
            )
        }
        return DeletionOutcome(deletedCount = applied.size, freedBytes = applied.sumOf { bytesById.getValue(it.photoId) })
    }

    private companion object {
        // SQLite allows 999 variables per statement on older Android versions
        const val SQL_VARIABLE_LIMIT = 500
    }
}
