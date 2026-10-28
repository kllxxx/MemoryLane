package com.kllxx.memorylane.core.photos.data.geocoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceFormatterTest {

    @Test
    fun `city and country`() {
        assertEquals("Светлогорск, Россия", formatPlace("Светлогорск", null, null, "Россия"))
    }

    @Test
    fun `the locality wins over the sub-admin area and the admin area`() {
        assertEquals("Paris, France", formatPlace("Paris", "Paris Department", "Île-de-France", "France"))
    }

    @Test
    fun `without a locality the sub-admin area is used`() {
        assertEquals("Pushkin district, Russia", formatPlace(null, "Pushkin district", "Saint Petersburg", "Russia"))
    }

    @Test
    fun `without a locality or a sub-admin area the admin area is used`() {
        assertEquals("Bavaria, Germany", formatPlace(null, null, "Bavaria", "Germany"))
    }

    @Test
    fun `a blank part counts as missing`() {
        assertEquals("Pushkin district, Russia", formatPlace("  ", "Pushkin district", null, "Russia"))
        assertEquals("Berlin", formatPlace("Berlin", null, null, "  "))
    }

    @Test
    fun `a place without a country is just the place`() {
        assertEquals("Berlin", formatPlace("Berlin", null, null, null))
    }

    @Test
    fun `a place without a city is just the country`() {
        assertEquals("Iceland", formatPlace(null, null, null, "Iceland"))
    }

    @Test
    fun `a city that is its own country is written once`() {
        assertEquals("Singapore", formatPlace("Singapore", null, null, "Singapore"))
    }

    @Test
    fun `nothing known is null`() {
        assertNull(formatPlace(null, null, null, null))
        assertNull(formatPlace(" ", "", " ", ""))
    }

    @Test
    fun `parts are trimmed`() {
        assertEquals("Berlin, Germany", formatPlace(" Berlin ", null, null, " Germany "))
    }
}
