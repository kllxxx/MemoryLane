package com.kllxx.memorylane.core.photos.data.geocoder

import android.location.Address
import android.location.Geocoder
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationGeocoderTest {

    private val legacySdk = 32
    private val asyncSdk = 33
    private lateinit var geocoder: Geocoder

    @Before
    fun setUp() {
        geocoder = mockk()
    }

    private fun address(locality: String?, thoroughfare: String?) = mockk<Address> {
        every { this@mockk.locality } returns locality
        every { this@mockk.thoroughfare } returns thoroughfare
    }

    private fun TestScopeGeocoder(sdk: Int, scope: kotlinx.coroutines.test.TestScope) =
        LocationGeocoder({ geocoder }, sdk, StandardTestDispatcher(scope.testScheduler))

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
    fun `legacy formats city and street`() = runTest {
        stubLegacy(listOf(address("Berlin", "Unter den Linden")))
        assertEquals("Berlin, Unter den Linden", TestScopeGeocoder(legacySdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `legacy returns locality only when thoroughfare is null`() = runTest {
        stubLegacy(listOf(address("Berlin", null)))
        assertEquals("Berlin", TestScopeGeocoder(legacySdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `legacy returns null when locality is null`() = runTest {
        stubLegacy(listOf(address(null, "Unter den Linden")))
        assertNull(TestScopeGeocoder(legacySdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `legacy returns null for empty or null address list`() = runTest {
        stubLegacy(emptyList())
        assertNull(TestScopeGeocoder(legacySdk, this).getLocation(1.0, 2.0))
        stubLegacy(null)
        assertNull(TestScopeGeocoder(legacySdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `legacy returns null when the geocoder throws`() = runTest {
        @Suppress("DEPRECATION")
        every { geocoder.getFromLocation(any(), any(), any()) } throws IOException("no network")
        assertNull(TestScopeGeocoder(legacySdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `returns null when the geocoder cannot be created`() = runTest {
        val failing = LocationGeocoder(
            { throw IllegalStateException("unavailable") },
            legacySdk,
            StandardTestDispatcher(testScheduler),
        )
        assertNull(failing.getLocation(1.0, 2.0))
    }

    // --- API 33+ ----------------------------------------------------------------------------

    @Test
    fun `async formats city and street`() = runTest {
        stubAsync(listOf(address("Paris", "Rue de Rivoli")))
        assertEquals("Paris, Rue de Rivoli", TestScopeGeocoder(asyncSdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `async returns locality only when thoroughfare is null`() = runTest {
        stubAsync(listOf(address("Paris", null)))
        assertEquals("Paris", TestScopeGeocoder(asyncSdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `async returns null when locality is null`() = runTest {
        stubAsync(listOf(address(null, "Rue de Rivoli")))
        assertNull(TestScopeGeocoder(asyncSdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `async returns null for empty result`() = runTest {
        stubAsync(emptyList())
        assertNull(TestScopeGeocoder(asyncSdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `async returns null on listener error`() = runTest {
        every { geocoder.getFromLocation(any(), any(), any(), any<Geocoder.GeocodeListener>()) } answers {
            arg<Geocoder.GeocodeListener>(3).onError("boom")
        }
        assertNull(TestScopeGeocoder(asyncSdk, this).getLocation(1.0, 2.0))
    }

    @Test
    fun `async ignores a callback that arrives after cancellation`() = runTest {
        var listener: Geocoder.GeocodeListener? = null
        every { geocoder.getFromLocation(any(), any(), any(), any<Geocoder.GeocodeListener>()) } answers {
            listener = arg(3)
        }
        val job = launch { TestScopeGeocoder(asyncSdk, this@runTest).getLocation(1.0, 2.0) }
        runCurrent()

        job.cancel()
        runCurrent()
        listener!!.onGeocode(mutableListOf(address("Paris", null)))
        listener!!.onError("late")

        assertTrue(job.isCancelled)
    }

    @Test
    fun `async returns null when the geocoder throws`() = runTest {
        every {
            geocoder.getFromLocation(any(), any(), any(), any<Geocoder.GeocodeListener>())
        } throws IllegalArgumentException("bad coordinates")
        assertNull(TestScopeGeocoder(asyncSdk, this).getLocation(1.0, 2.0))
    }
}
