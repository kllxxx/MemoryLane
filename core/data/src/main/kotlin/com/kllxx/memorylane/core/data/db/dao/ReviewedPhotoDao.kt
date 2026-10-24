package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kllxx.memorylane.core.data.db.entity.ReviewedPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewedPhotoDao {
    /** Keeps the first row, so the id counts once however often it is swiped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ReviewedPhotoEntity)

    @Query("SELECT COUNT(*) FROM reviewed_photos")
    fun observeCount(): Flow<Int>
}
