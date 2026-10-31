package com.kllxx.memorylane.core.photos.data.di

import com.kllxx.memorylane.core.photos.data.date.ExifDateReader
import com.kllxx.memorylane.core.photos.data.date.MediaStoreExifDateReader
import com.kllxx.memorylane.core.photos.data.repository.AndroidDeleteCapabilities
import com.kllxx.memorylane.core.photos.domain.repository.DeleteCapabilities
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.core.photos.data.repository.PhotoRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PhotosModule {
    @Binds
    abstract fun bindPhotoRepository(impl: PhotoRepositoryImpl): PhotoRepository

    @Binds
    abstract fun bindDeleteCapabilities(impl: AndroidDeleteCapabilities): DeleteCapabilities

    @Binds
    abstract fun bindExifDateReader(impl: MediaStoreExifDateReader): ExifDateReader
}
