package com.kllxx.memorylane.core.domain.format

private const val BYTES_PER_MB = 1_000_000L
private const val BYTES_PER_GB = 1_000_000_000L
private const val BYTES_PER_TENTH_GB = BYTES_PER_GB / 10
private const val TENTHS_PER_GB = 10L

enum class SizeUnit { MB, GB }

/** The number and its unit are shown as separate texts. */
data class FormattedSize(val value: String, val unit: SizeUnit)

/**
 * SI units like the Android storage screens (1 GB = 1000³ bytes): whole MB below 1 GB (never rounded
 * up into 1000), above that one decimal with a comma and no trailing `,0`.
 */
fun formatSize(bytes: Long): FormattedSize {
    if (bytes < BYTES_PER_GB) return FormattedSize((bytes / BYTES_PER_MB).toString(), SizeUnit.MB)
    val tenths = (bytes + BYTES_PER_TENTH_GB / 2) / BYTES_PER_TENTH_GB
    val whole = tenths / TENTHS_PER_GB
    val fraction = tenths % TENTHS_PER_GB
    return FormattedSize(if (fraction == 0L) "$whole" else "$whole,$fraction", SizeUnit.GB)
}
