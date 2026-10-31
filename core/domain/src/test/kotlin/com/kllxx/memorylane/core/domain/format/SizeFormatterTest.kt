package com.kllxx.memorylane.core.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeFormatterTest {

    private fun size(bytes: Long) = formatSize(bytes).let { it.value to it.unit }

    @Test
    fun `zero bytes is zero kilobytes`() {
        assertEquals("0" to SizeUnit.KB, size(0))
    }

    @Test
    fun `size below a megabyte is whole kilobytes`() {
        assertEquals("730" to SizeUnit.KB, size(730_000))
        assertEquals("999" to SizeUnit.KB, size(999_000))
    }

    @Test
    fun `kilobytes are rounded, not truncated`() {
        assertEquals("1" to SizeUnit.KB, size(500))
        assertEquals("0" to SizeUnit.KB, size(499))
        assertEquals("731" to SizeUnit.KB, size(730_500))
    }

    @Test
    fun `kilobytes that round up to 1000 become one megabyte`() {
        assertEquals("999" to SizeUnit.KB, size(999_499))
        assertEquals("1" to SizeUnit.MB, size(999_500))
        assertEquals("1" to SizeUnit.MB, size(999_999))
    }

    @Test
    fun `size of one megabyte drops the trailing zero`() {
        assertEquals("1" to SizeUnit.MB, size(1_000_000))
    }

    @Test
    fun `megabytes below ten have one rounded decimal with a comma`() {
        assertEquals("1,5" to SizeUnit.MB, size(1_470_000))
        assertEquals("1,4" to SizeUnit.MB, size(1_449_999))
        assertEquals("9,9" to SizeUnit.MB, size(9_940_000))
    }

    @Test
    fun `megabytes that round up to ten show as whole ten`() {
        assertEquals("10" to SizeUnit.MB, size(9_950_000))
        assertEquals("10" to SizeUnit.MB, size(9_999_999))
    }

    @Test
    fun `megabytes from ten are whole and rounded`() {
        assertEquals("10" to SizeUnit.MB, size(10_400_000))
        assertEquals("11" to SizeUnit.MB, size(10_500_000))
        assertEquals("640" to SizeUnit.MB, size(640_000_000))
    }

    @Test
    fun `megabytes that round up to 1000 become one gigabyte`() {
        assertEquals("999" to SizeUnit.MB, size(999_499_999))
        assertEquals("1" to SizeUnit.GB, size(999_500_000))
        assertEquals("1" to SizeUnit.GB, size(999_999_999))
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
