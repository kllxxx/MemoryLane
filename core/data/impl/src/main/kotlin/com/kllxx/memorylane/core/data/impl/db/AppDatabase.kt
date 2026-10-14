package com.kllxx.memorylane.core.data.impl.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kllxx.memorylane.core.data.impl.db.dao.PendingDeleteDao
import com.kllxx.memorylane.core.data.impl.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.impl.db.entity.PendingDeleteEntity
import com.kllxx.memorylane.core.data.impl.db.entity.SwipeSessionEntity

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
