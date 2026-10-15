package com.kllxx.memorylane.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kllxx.memorylane.core.data.db.dao.PendingDeleteDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.db.entity.PendingDeleteEntity
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity

@Database(
    entities = [PendingDeleteEntity::class, SwipeSessionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingDeleteDao(): PendingDeleteDao
    abstract fun swipeSessionDao(): SwipeSessionDao
}
