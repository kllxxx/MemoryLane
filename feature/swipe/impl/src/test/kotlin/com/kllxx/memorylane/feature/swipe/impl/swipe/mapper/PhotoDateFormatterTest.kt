package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDateFormatterTest {

    private val ru = Locale.forLanguageTag("ru-RU")

    @Test
    fun `Russian is the full date with the month in the genitive and the 24-hour clock`() {
        assertEquals("7 января 2016, 21:15", formatTakenAt(LocalDateTime.of(2016, 1, 7, 21, 15), ru))
    }

    @Test
    fun `Russian has no leading zero on the day and keeps the zero of the minutes`() {
        assertEquals("1 марта 2024, 09:05", formatTakenAt(LocalDateTime.of(2024, 3, 1, 9, 5), ru))
    }

    @Test
    fun `Russian midnight and noon use the 24-hour clock`() {
        assertEquals("15 октября 2020, 00:00", formatTakenAt(LocalDateTime.of(2020, 10, 15, 0, 0), ru))
        assertEquals("15 октября 2020, 12:00", formatTakenAt(LocalDateTime.of(2020, 10, 15, 12, 0), ru))
    }

    @Test
    fun `every Russian month is in the genitive`() {
        val expected = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )
        expected.forEachIndexed { index, month ->
            assertEquals(
                "7 $month 2016, 10:00",
                formatTakenAt(LocalDateTime.of(2016, index + 1, 7, 10, 0), ru),
            )
        }
    }

    @Test
    fun `no Russian month is in the nominative`() {
        val nominative = listOf(
            "январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
        )
        (1..12).forEach { month ->
            val text = formatTakenAt(LocalDateTime.of(2016, month, 7, 10, 0), ru)
            nominative.forEach { name -> assertTrue("$text has $name", !text.split(' ').contains(name)) }
        }
    }

    @Test
    fun `the Russian month does not depend on the default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("8 января 2016, 00:07", formatTakenAt(LocalDateTime.of(2016, 1, 8, 0, 7), ru))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `seconds are not shown`() {
        assertEquals("7 января 2016, 21:15", formatTakenAt(LocalDateTime.of(2016, 1, 7, 21, 15, 59), ru))
    }

    @Test
    fun `another language uses its own long date with the year and a short time`() {
        val text = formatTakenAt(LocalDateTime.of(2016, 1, 7, 21, 15), Locale.US)

        assertTrue(text, text.contains("January"))
        assertTrue(text, text.contains("2016"))
        assertTrue(text, text.contains("9:15"))
    }

    @Test
    fun `English follows the locale rather than the Russian pattern`() {
        val text = formatTakenAt(LocalDateTime.of(2016, 1, 7, 21, 15), Locale.UK)

        assertTrue(text, text.contains("January"))
        assertTrue(text, text.contains("21:15"))
    }

    // --- a photo that has only a day ---

    @Test
    fun `Russian without a time is the date alone`() {
        assertEquals("7 января 2016", formatTakenAt(LocalDateTime.of(2016, 1, 7, 12, 0), ru, hasTime = false))
    }

    @Test
    fun `every Russian month without a time is in the genitive`() {
        val expected = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )
        expected.forEachIndexed { index, month ->
            assertEquals("7 $month 2016", formatTakenAt(LocalDateTime.of(2016, index + 1, 7, 12, 0), ru, hasTime = false))
        }
    }

    @Test
    fun `without a time the placeholder time is never shown`() {
        val text = formatTakenAt(LocalDateTime.of(2016, 1, 7, 12, 0), ru, hasTime = false)

        assertTrue(text, !text.contains(":"))
        assertTrue(text, !text.contains("12"))
    }

    @Test
    fun `a real midnight is shown when the photo has a time`() {
        assertEquals("8 января 2016, 00:00", formatTakenAt(LocalDateTime.of(2016, 1, 8, 0, 0), ru, hasTime = true))
        assertEquals("8 января 2016, 00:00", formatTakenAt(LocalDateTime.of(2016, 1, 8, 0, 0), ru))
    }

    @Test
    fun `another language without a time is its long date alone`() {
        val text = formatTakenAt(LocalDateTime.of(2016, 1, 7, 12, 0), Locale.US, hasTime = false)

        assertTrue(text, text.contains("January"))
        assertTrue(text, text.contains("2016"))
        assertTrue(text, !text.contains(":"))
    }

    // --- the day and the month ---

    @Test
    fun `the day and the month are in the genitive for every Russian month`() {
        val expected = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )
        expected.forEachIndexed { index, month ->
            assertEquals("28 $month", formatDayMonth(java.time.LocalDate.of(2026, index + 1, 28), ru))
        }
    }

    @Test
    fun `the day has no leading zero`() {
        assertEquals("5 марта", formatDayMonth(java.time.LocalDate.of(2026, 3, 5), ru))
    }

    @Test
    fun `another language has its own month name`() {
        assertEquals("28 October", formatDayMonth(java.time.LocalDate.of(2026, 10, 28), Locale.UK))
    }
}
