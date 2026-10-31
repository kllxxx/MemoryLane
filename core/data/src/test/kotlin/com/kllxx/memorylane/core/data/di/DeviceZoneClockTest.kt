package com.kllxx.memorylane.core.data.di

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone

class DeviceZoneClockTest {

    private val original = TimeZone.getDefault()

    @After
    fun tearDown() {
        TimeZone.setDefault(original)
    }

    @Test
    fun `the zone follows a change of the device timezone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(ZoneId.of("Asia/Tokyo"), DeviceZoneClock.zone)

        TimeZone.setDefault(TimeZone.getTimeZone("America/Denver"))
        assertEquals(ZoneId.of("America/Denver"), DeviceZoneClock.zone)
    }

    @Test
    fun `withZone gives a clock fixed to that zone`() {
        val utc = DeviceZoneClock.withZone(ZoneId.of("UTC"))

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

        assertEquals(ZoneId.of("UTC"), utc.zone)
    }
}
