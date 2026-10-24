package com.kllxx.memorylane.core.data.di

import com.kllxx.memorylane.core.data.notification.NotificationStatusProviderImpl
import com.kllxx.memorylane.core.domain.notification.NotificationStatusProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationBindsModule {
    @Binds
    abstract fun bindNotificationStatusProvider(impl: NotificationStatusProviderImpl): NotificationStatusProvider
}
