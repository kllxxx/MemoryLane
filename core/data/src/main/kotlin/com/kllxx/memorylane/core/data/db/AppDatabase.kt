package com.kllxx.memorylane.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kllxx.memorylane.core.data.db.dao.DeletionDao
import com.kllxx.memorylane.core.data.db.dao.PhotoDateCacheDao
import com.kllxx.memorylane.core.data.db.dao.PhotoDecisionDao
import com.kllxx.memorylane.core.data.db.dao.PhotoLocationCacheDao
import com.kllxx.memorylane.core.data.db.dao.ReviewedPhotoDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.db.entity.PhotoDateCacheEntity
import com.kllxx.memorylane.core.data.db.entity.PhotoDecisionEntity
import com.kllxx.memorylane.core.data.db.entity.PhotoLocationCacheEntity
import com.kllxx.memorylane.core.data.db.entity.ReviewedPhotoEntity
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity

@Database(
    entities = [
        PhotoDecisionEntity::class,
        SwipeSessionEntity::class,
        ReviewedPhotoEntity::class,
        PhotoDateCacheEntity::class,
        PhotoLocationCacheEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDecisionDao(): PhotoDecisionDao
    abstract fun swipeSessionDao(): SwipeSessionDao
    abstract fun deletionDao(): DeletionDao
    abstract fun reviewedPhotoDao(): ReviewedPhotoDao
    abstract fun photoDateCacheDao(): PhotoDateCacheDao
    abstract fun photoLocationCacheDao(): PhotoLocationCacheDao
}
