package com.kllxx.memorylane.core.photos.impl.di

import com.kllxx.memorylane.core.photos.api.repository.PhotoRepository
import com.kllxx.memorylane.core.photos.impl.repository.PhotoRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PhotosModule {
    @Binds
    abstract fun bindPhotoRepository(impl: PhotoRepositoryImpl): PhotoRepository
}
