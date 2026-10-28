package com.kllxx.memorylane.core.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeFormatterTest {

    private fun size(bytes: Long) = formatSize(bytes).let { it.value to it.unit }


    @Test
    fun `size below one megabyte is zero megabytes`() {
        assertEquals("0" to SizeUnit.MB, size(0))
        assertEquals("0" to SizeUnit.MB, size(999_999))
    }

    @Test
    fun `size of one megabyte`() {
        assertEquals("1" to SizeUnit.MB, size(1_000_000))
    }

    @Test
    fun `size below a gigabyte is whole megabytes`() {
        assertEquals("640" to SizeUnit.MB, size(640_000_000))
    }

    @Test
    fun `size just below a gigabyte never rounds up to 1000 megabytes`() {
        assertEquals("999" to SizeUnit.MB, size(999_900_000))
        assertEquals("999" to SizeUnit.MB, size(999_999_999))
    }

    @Test
    fun `size of one gigabyte drops the trailing zero`() {
        assertEquals("1" to SizeUnit.GB, size(1_000_000_000))
    }

    @Test
    fun `size above a gigabyte has one decimal with a comma`() {
        assertEquals("2,4" to SizeUnit.GB, size(2_440_000_000))
        assertEquals("2,5" to SizeUnit.GB, size(2_450_000_000))
    }

    @Test
    fun `a whole number of gigabytes has no decimal`() {
        assertEquals("12" to SizeUnit.GB, size(12_000_000_000))
        assertEquals("12" to SizeUnit.GB, size(11_960_000_000))
    }
}
