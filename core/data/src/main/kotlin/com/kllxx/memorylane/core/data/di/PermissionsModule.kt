package com.kllxx.memorylane.core.data.di

import com.kllxx.memorylane.core.data.permission.PermissionsRepositoryImpl
import com.kllxx.memorylane.core.domain.repository.PermissionsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PermissionsBindsModule {
    @Binds
    abstract fun bindPermissionsRepository(impl: PermissionsRepositoryImpl): PermissionsRepository
}
