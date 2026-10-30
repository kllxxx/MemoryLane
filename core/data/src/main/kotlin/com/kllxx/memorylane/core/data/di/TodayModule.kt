package com.kllxx.memorylane.core.data.di

import android.content.Context
import com.kllxx.memorylane.core.data.time.SystemTodayProvider
import com.kllxx.memorylane.core.data.time.dateChangeEvents
import com.kllxx.memorylane.core.domain.time.TodayProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object TodayModule {
    @Provides
    @Singleton
    fun provideTodayProvider(clock: Clock, @ApplicationContext context: Context): TodayProvider =
        SystemTodayProvider(clock, dateChangeEvents(context))
}
