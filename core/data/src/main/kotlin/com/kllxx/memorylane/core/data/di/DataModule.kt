package com.kllxx.memorylane.core.data.di

import android.content.Context
import androidx.room.Room
import com.kllxx.memorylane.core.photos.domain.repository.PhotoDateCache
import com.kllxx.memorylane.core.photos.domain.repository.PhotoLocationCache
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.MIGRATION_1_2
import com.kllxx.memorylane.core.data.db.MIGRATION_2_3
import com.kllxx.memorylane.core.data.db.MIGRATION_3_4
import com.kllxx.memorylane.core.data.db.MIGRATION_4_5
import com.kllxx.memorylane.core.data.db.MIGRATION_5_6
import com.kllxx.memorylane.core.data.db.MIGRATION_6_7
import com.kllxx.memorylane.core.data.db.dao.DeletionDao
import com.kllxx.memorylane.core.data.db.dao.PhotoDateCacheDao
import com.kllxx.memorylane.core.data.db.dao.PhotoDecisionDao
import com.kllxx.memorylane.core.data.db.dao.PhotoLocationCacheDao
import com.kllxx.memorylane.core.data.db.dao.ReviewedPhotoDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.repository.PhotoDateCacheImpl
import com.kllxx.memorylane.core.data.repository.PhotoLocationCacheImpl
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

    @Binds
    abstract fun bindPhotoDateCache(impl: PhotoDateCacheImpl): PhotoDateCache

    @Binds
    abstract fun bindPhotoLocationCache(impl: PhotoLocationCacheImpl): PhotoLocationCache
}

@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidesModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DataConstants.DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides
    fun providePhotoDecisionDao(db: AppDatabase): PhotoDecisionDao = db.photoDecisionDao()

    @Provides
    fun provideSwipeSessionDao(db: AppDatabase): SwipeSessionDao = db.swipeSessionDao()

    @Provides
    fun provideDeletionDao(db: AppDatabase): DeletionDao = db.deletionDao()

    @Provides
    fun provideReviewedPhotoDao(db: AppDatabase): ReviewedPhotoDao = db.reviewedPhotoDao()

    @Provides
    fun providePhotoDateCacheDao(db: AppDatabase): PhotoDateCacheDao = db.photoDateCacheDao()

    @Provides
    fun providePhotoLocationCacheDao(db: AppDatabase): PhotoLocationCacheDao = db.photoLocationCacheDao()
}
