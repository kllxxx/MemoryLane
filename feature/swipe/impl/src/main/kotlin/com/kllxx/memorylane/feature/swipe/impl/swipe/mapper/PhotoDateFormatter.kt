package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val RU_PATTERN = "d MMMM yyyy, HH:mm"

/**
 * «7 января 2016, 21:15»: the full date with the 24-hour clock in Russian; any other language uses its own long
 * date and short time.
 */
fun formatTakenAt(takenAt: LocalDateTime, locale: Locale): String {
    val formatter = if (locale.language == "ru") {
        DateTimeFormatter.ofPattern(RU_PATTERN, locale)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale)
    }
    return formatter.format(takenAt)
}
