package com.kllxx.memorylane.di

import android.content.Context
import com.kllxx.memorylane.core.domain.model.AppInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AppInfoModule {
    @Provides
    @Singleton
    fun provideAppInfo(@ApplicationContext context: Context): AppInfo {
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        return AppInfo(versionName = versionName.orEmpty())
    }
}
