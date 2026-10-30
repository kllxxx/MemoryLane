package com.kllxx.memorylane.di

import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.TimberErrorHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object ErrorModule {
    @Provides
    @Singleton
    fun provideErrorHandler(): ErrorHandler = TimberErrorHandler()
}
