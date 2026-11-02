package com.kllxx.memorylane.core.domain.format

import java.text.DecimalFormatSymbols
import java.util.Locale

private const val BYTES_PER_KB = 1_000L
private const val BYTES_PER_TENTH_MB = 100_000L
private const val BYTES_PER_MB = 1_000_000L
private const val BYTES_PER_TENTH_GB = 100_000_000L
private const val KB_PER_MB = 1_000L
private const val TENTHS_BELOW_TEN_MB = 100L
private const val MB_PER_GB = 1_000L
private const val TENTHS = 10L

enum class SizeUnit { KB, MB, GB }

/**
 * The number and its unit are shown as separate texts. [value] is locale-neutral, a `.` before the tenth («2.4»);
 * [text] puts the decimal separator of the language on screen: «2,4» in Russian, «2.4» in English.
 */
data class FormattedSize(val value: String, val unit: SizeUnit) {
    fun text(locale: Locale): String = value.replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
}

/**
 * SI units like the Android storage screens (1000 bytes = 1 KB). Every tier is rounded, and a value that
 * rounds up to the next tier is shown in that tier (999.6 KB is "1 MB", not "1000 KB"):
 * - below 1 MB: whole KB;
 * - from 1 MB to 10 MB: one decimal and no trailing `.0`;
 * - from 10 MB to 1 GB: whole MB;
 * - from 1 GB: one decimal and no trailing `.0`.
 */
fun formatSize(bytes: Long): FormattedSize {
    val kb = (bytes + BYTES_PER_KB / 2) / BYTES_PER_KB
    if (kb < KB_PER_MB) return FormattedSize(kb.toString(), SizeUnit.KB)

    val tenthsOfMb = (bytes + BYTES_PER_TENTH_MB / 2) / BYTES_PER_TENTH_MB
    if (tenthsOfMb < TENTHS_BELOW_TEN_MB) return FormattedSize(withTenths(tenthsOfMb), SizeUnit.MB)

    val mb = (bytes + BYTES_PER_MB / 2) / BYTES_PER_MB
    if (mb < MB_PER_GB) return FormattedSize(mb.toString(), SizeUnit.MB)

    val tenthsOfGb = (bytes + BYTES_PER_TENTH_GB / 2) / BYTES_PER_TENTH_GB
    return FormattedSize(withTenths(tenthsOfGb), SizeUnit.GB)
}

private fun withTenths(tenths: Long): String {
    val whole = tenths / TENTHS
    val fraction = tenths % TENTHS
    return if (fraction == 0L) "$whole" else "$whole.$fraction"
}
