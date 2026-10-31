package com.kllxx.memorylane.core.common.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** A scope that lives as long as the process, for work that must not stop with a screen or a receiver. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
