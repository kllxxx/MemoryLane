package com.kllxx.memorylane.core.data.di

import android.content.Context
import androidx.room.Room
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.MIGRATION_1_2
import com.kllxx.memorylane.core.data.db.MIGRATION_2_3
import com.kllxx.memorylane.core.data.db.MIGRATION_3_4
import com.kllxx.memorylane.core.data.db.dao.PendingDeleteDao
import com.kllxx.memorylane.core.data.db.dao.ReviewedPhotoDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.repository.StatsRepositoryImpl
import com.kllxx.memorylane.core.data.repository.SwipeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataBindsModule {
    @Binds
    abstract fun bindSwipeRepository(impl: SwipeRepositoryImpl): SwipeRepository

    @Binds
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidesModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DataConstants.DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun providePendingDeleteDao(db: AppDatabase): PendingDeleteDao = db.pendingDeleteDao()

    @Provides
    fun provideSwipeSessionDao(db: AppDatabase): SwipeSessionDao = db.swipeSessionDao()

    @Provides
    fun provideReviewedPhotoDao(db: AppDatabase): ReviewedPhotoDao = db.reviewedPhotoDao()
}
