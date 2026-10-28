package com.kllxx.memorylane.core.photos.data.geocoder

import android.location.Address
import android.location.Geocoder
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationGeocoderTest {

    private val legacySdk = 32
    private val asyncSdk = 33
    private lateinit var geocoder: Geocoder

    @Before
    fun setUp() {
        geocoder = mockk()
    }

    private fun address(
        locality: String? = null,
        subAdminArea: String? = null,
        adminArea: String? = null,
        country: String? = null,
    ) = mockk<Address> {
        every { this@mockk.locality } returns locality
        every { this@mockk.subAdminArea } returns subAdminArea
        every { this@mockk.adminArea } returns adminArea
        every { this@mockk.countryName } returns country
    }

    private fun geocoder(sdk: Int, scope: TestScope, present: Boolean = true) =
        LocationGeocoder({ geocoder }, sdk, StandardTestDispatcher(scope.testScheduler), { present })

    @Suppress("DEPRECATION")
    private fun stubLegacy(result: List<Address>?) {
        every { geocoder.getFromLocation(any(), any(), any()) } returns result
    }

    private fun stubAsync(addresses: List<Address>) {
        every { geocoder.getFromLocation(any(), any(), any(), any<Geocoder.GeocodeListener>()) } answers {
            arg<Geocoder.GeocodeListener>(3).onGeocode(addresses.toMutableList())
        }
    }

    // --- below API 33 -----------------------------------------------------------------------

    @Test
    fun `legacy formats city and country`() = runTest {
        stubLegacy(listOf(address(locality = "Berlin", country = "Germany")))
        assertEquals(GeocodeResult.Found("Berlin, Germany"), geocoder(legacySdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `legacy falls back from the locality to the sub-admin area and then the admin area`() = runTest {
        stubLegacy(listOf(address(subAdminArea = "Pushkin district", adminArea = "Saint Petersburg", country = "Russia")))
        assertEquals(
            GeocodeResult.Found("Pushkin district, Russia"),
            geocoder(legacySdk, this).getLocation(1.0, 2.0),
        )
        stubLegacy(listOf(address(adminArea = "Saint Petersburg", country = "Russia")))
        assertEquals(
            GeocodeResult.Found("Saint Petersburg, Russia"),
            geocoder(legacySdk, this).getLocation(1.0, 2.0),
        )
    }

    @Test
    fun `legacy is no result for an address with nothing usable`() = runTest {
        stubLegacy(listOf(address()))
        assertEquals(GeocodeResult.NoResult, geocoder(legacySdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `legacy is no result for an empty or null address list`() = runTest {
        stubLegacy(emptyList())
        assertEquals(GeocodeResult.NoResult, geocoder(legacySdk, this).getLocation(1.0, 2.0))
        stubLegacy(null)
        assertEquals(GeocodeResult.NoResult, geocoder(legacySdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `legacy is a failure when the geocoder throws`() = runTest {
        @Suppress("DEPRECATION")
        every { geocoder.getFromLocation(any(), any(), any()) } throws IOException("no network")
        assertEquals(GeocodeResult.Failure, geocoder(legacySdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `it is a failure when the geocoder cannot be created`() = runTest {
        val failing = LocationGeocoder(
            { throw IllegalStateException("unavailable") },
            legacySdk,
            StandardTestDispatcher(testScheduler),
            { true },
        )
        assertEquals(GeocodeResult.Failure, failing.getLocation(1.0, 2.0))
    }

    @Test
    fun `it is a failure, and never asks, when the device has no geocoder`() = runTest {
        // A missing service says nothing about the position, so it must not be remembered as "no place"
        val result = geocoder(legacySdk, this, present = false).getLocation(1.0, 2.0)

        assertEquals(GeocodeResult.Failure, result)
        io.mockk.verify(exactly = 0) { geocoder.getFromLocation(any(), any(), any(), any<Geocoder.GeocodeListener>()) }
    }

    @Test
    fun `the geocoder is built for every lookup, so the current language is used`() = runTest {
        var built = 0
        stubLegacy(listOf(address(locality = "Paris", country = "France")))
        val counting = LocationGeocoder(
            { built++; geocoder },
            legacySdk,
            StandardTestDispatcher(testScheduler),
            { true },
        )

        counting.getLocation(1.0, 2.0)
        counting.getLocation(1.0, 2.0)

        assertEquals(2, built)
    }

    // --- API 33+ ----------------------------------------------------------------------------

    @Test
    fun `async formats city and country`() = runTest {
        stubAsync(listOf(address(locality = "Paris", country = "France")))
        assertEquals(GeocodeResult.Found("Paris, France"), geocoder(asyncSdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `async falls back to the admin area`() = runTest {
        stubAsync(listOf(address(adminArea = "Bavaria", country = "Germany")))
        assertEquals(GeocodeResult.Found("Bavaria, Germany"), geocoder(asyncSdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `async is no result for an empty result`() = runTest {
        stubAsync(emptyList())
        assertEquals(GeocodeResult.NoResult, geocoder(asyncSdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `async is a failure on a listener error`() = runTest {
        every { geocoder.getFromLocation(any(), any(), any(), any<Geocoder.GeocodeListener>()) } answers {
            arg<Geocoder.GeocodeListener>(3).onError("boom")
        }
        assertEquals(GeocodeResult.Failure, geocoder(asyncSdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `async ignores a callback that arrives after cancellation`() = runTest {
        var listener: Geocoder.GeocodeListener? = null
        every { geocoder.getFromLocation(any(), any(), any(), any<Geocoder.GeocodeListener>()) } answers {
            listener = arg(3)
        }
        val job = launch { geocoder(asyncSdk, this@runTest).getLocation(1.0, 2.0) }
        runCurrent()

        job.cancel()
        runCurrent()
        listener!!.onGeocode(mutableListOf(address(locality = "Paris", country = "France")))
        listener!!.onError("late")

        assertTrue(job.isCancelled)
    }

    @Test
    fun `async is a failure when the geocoder throws`() = runTest {
        every {
            geocoder.getFromLocation(any(), any(), any(), any<Geocoder.GeocodeListener>())
        } throws IllegalArgumentException("bad coordinates")
        assertEquals(GeocodeResult.Failure, geocoder(asyncSdk, this).getLocation(1.0, 2.0))
    }
}
