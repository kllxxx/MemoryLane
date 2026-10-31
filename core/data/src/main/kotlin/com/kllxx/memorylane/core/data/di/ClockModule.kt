package com.kllxx.memorylane.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
internal object ClockModule {
    @Provides
    fun provideClock(): Clock = DeviceZoneClock
}

/**
 * The system clock in the zone the device has right now. [Clock.systemDefaultZone] fixes the zone when it is created,
 * so a timezone change while the process lives would leave everything that reads [getZone] on the old one.
 */
internal object DeviceZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}
