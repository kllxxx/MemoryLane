package com.kllxx.memorylane.di

import com.kllxx.memorylane.core.domain.language.AppLanguageController
import com.kllxx.memorylane.language.AppCompatLanguageController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LanguageModule {
    @Binds
    @Singleton
    abstract fun bindAppLanguageController(impl: AppCompatLanguageController): AppLanguageController
}
