package com.kllxx.memorylane.core.photos.data.repository

import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnThisDayIndexBuilderTest {

    private val utc = ZoneOffset.UTC
    private val currentYear = 2026

    private fun builder(zone: ZoneId = utc, maxYearsBack: Int = 30) = OnThisDayIndexBuilder(currentYear, maxYearsBack, zone)

    private fun millis(date: LocalDate, hour: Int = 12, zone: ZoneId = utc) =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun OnThisDayIndexBuilder.add(id: Long, date: LocalDate, hour: Int = 12) = add(IndexedImage(id, millis(date, hour)))

    @Test
    fun `no images give an empty index`() {
        assertTrue(builder().entries().isEmpty())
        assertEquals(0, builder().counted)
    }

    @Test
    fun `images of the same month and day in different years are grouped`() {
        val builder = builder()
        builder.add(1, LocalDate.of(2025, 3, 9))
        builder.add(2, LocalDate.of(2023, 3, 9))
        builder.add(3, LocalDate.of(2020, 3, 9))
        builder.add(4, LocalDate.of(2024, 3, 10))

        val entries = builder.entries()

        assertEquals(2, entries.size)
        assertEquals(3, entries.getValue(MonthDay.of(3, 9)).first)
        assertEquals(1, entries.getValue(MonthDay.of(3, 10)).first)
        assertEquals(4, builder.counted)
    }

    @Test
    fun `the cover is the oldest image of the day`() {
        val builder = builder()
        builder.add(1, LocalDate.of(2025, 3, 9))
        builder.add(2, LocalDate.of(2019, 3, 9))
        builder.add(3, LocalDate.of(2022, 3, 9))

        val cover = builder.entries().getValue(MonthDay.of(3, 9)).second

        assertEquals(2L, cover.id)
        assertEquals(LocalDate.of(2019, 3, 9), cover.takenOn)
    }

    @Test
    fun `two images taken at the same moment pick the lowest id as cover`() {
        val builder = builder()
        builder.add(9, LocalDate.of(2020, 3, 9))
        builder.add(4, LocalDate.of(2020, 3, 9))

        assertEquals(4L, builder.entries().getValue(MonthDay.of(3, 9)).second.id)
    }

    @Test
    fun `the current year and later years are not memories`() {
        val builder = builder()
        builder.add(1, LocalDate.of(2026, 3, 9))
        builder.add(2, LocalDate.of(2027, 3, 9))

        assertTrue(builder.entries().isEmpty())
    }

    @Test
    fun `only the last 30 years count`() {
        val builder = builder()
        builder.add(1, LocalDate.of(2025, 5, 1))
        builder.add(2, LocalDate.of(1996, 5, 1))
        builder.add(3, LocalDate.of(1995, 5, 1))

        // 2026 - 30 = 1996 is the oldest year in the window
        assertEquals(2, builder.entries().getValue(MonthDay.of(5, 1)).first)
        assertEquals(2L, builder.entries().getValue(MonthDay.of(5, 1)).second.id)
    }

    @Test
    fun `a Feb 29 image stays on Feb 29 and is never moved to Feb 28 or Mar 1`() {
        val builder = builder()
        builder.add(1, LocalDate.of(2024, 2, 29))
        builder.add(2, LocalDate.of(2025, 2, 28))
        builder.add(3, LocalDate.of(2025, 3, 1))

        val entries = builder.entries()

        assertEquals(1, entries.getValue(MonthDay.of(2, 29)).first)
        assertEquals(1, entries.getValue(MonthDay.of(2, 28)).first)
        assertEquals(1, entries.getValue(MonthDay.of(3, 1)).first)
    }

    @Test
    fun `the day is the local day of the device zone`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val builder = builder(zone)
        // 23:30 in Tokyo on the 9th is still the 9th in UTC+9, but the 9th 14:30 in UTC
        builder.add(IndexedImage(1, millis(LocalDate.of(2020, 3, 9), hour = 23, zone = zone)))

        assertEquals(setOf(MonthDay.of(3, 9)), builder.entries().keys)
    }

    @Test
    fun `a custom look-back limit narrows the window`() {
        val builder = builder(maxYearsBack = 2)
        builder.add(1, LocalDate.of(2024, 7, 7))
        builder.add(2, LocalDate.of(2023, 7, 7))

        assertEquals(1, builder.entries().getValue(MonthDay.of(7, 7)).first)
    }

    // --- which moment a gallery row means ---

    @Test
    fun `DATE_TAKEN is used when it is set`() {
        assertEquals(5_000L, effectiveTakenAtMillis(dateTaken = 5_000L, dateAddedSeconds = 9L))
    }

    @Test
    fun `a missing or zero DATE_TAKEN falls back to DATE_ADDED seconds as milliseconds`() {
        assertEquals(9_000L, effectiveTakenAtMillis(dateTaken = null, dateAddedSeconds = 9L))
        assertEquals(9_000L, effectiveTakenAtMillis(dateTaken = 0L, dateAddedSeconds = 9L))
    }

    @Test
    fun `a row with neither date has no moment`() {
        assertNull(effectiveTakenAtMillis(null, null))
        assertNull(effectiveTakenAtMillis(0L, 0L))
        assertNull(effectiveTakenAtMillis(0L, null))
    }
}
