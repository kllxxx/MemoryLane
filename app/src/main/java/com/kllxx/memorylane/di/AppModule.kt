package com.kllxx.memorylane.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
internal object AppModule {
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
