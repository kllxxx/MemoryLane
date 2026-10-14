package com.kllxx.memorylane.core.data.impl.di

import android.content.Context
import androidx.room.Room
import com.kllxx.memorylane.core.data.api.repository.SwipeRepository
import com.kllxx.memorylane.core.data.impl.db.AppDatabase
import com.kllxx.memorylane.core.data.impl.db.dao.PendingDeleteDao
import com.kllxx.memorylane.core.data.impl.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.impl.repository.SwipeRepositoryImpl
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
}

@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidesModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "memorylane.db").build()

    @Provides
    fun providePendingDeleteDao(db: AppDatabase): PendingDeleteDao = db.pendingDeleteDao()

    @Provides
    fun provideSwipeSessionDao(db: AppDatabase): SwipeSessionDao = db.swipeSessionDao()
}
