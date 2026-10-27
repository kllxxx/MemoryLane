package com.kllxx.memorylane.core.photos.data.di

import com.kllxx.memorylane.core.photos.data.date.ExifDateReader
import com.kllxx.memorylane.core.photos.data.date.MediaStoreExifDateReader
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.core.photos.data.repository.PhotoRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PhotosModule {
    @Binds
    abstract fun bindPhotoRepository(impl: PhotoRepositoryImpl): PhotoRepository

    @Binds
    abstract fun bindExifDateReader(impl: MediaStoreExifDateReader): ExifDateReader
}

@Module
@InstallIn(SingletonComponent::class)
internal object PhotosProvidesModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
