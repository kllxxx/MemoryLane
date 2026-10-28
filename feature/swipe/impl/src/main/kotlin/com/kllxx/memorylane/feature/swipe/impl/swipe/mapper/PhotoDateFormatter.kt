package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * The months of the "d MMMM" form, the genitive: «7 января». The app's `java.time` is the desugared backport, whose
 * `MMMM` gives the nominative «январь» on a device, so the names are not left to the platform.
 */
private val RU_MONTHS = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

private val RU_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendValue(ChronoField.DAY_OF_MONTH)
    .appendLiteral(' ')
    .appendText(ChronoField.MONTH_OF_YEAR, RU_MONTHS.withIndex().associate { (index, name) -> (index + 1L) to name })
    .appendPattern(" yyyy, HH:mm")
    .toFormatter(Locale.forLanguageTag("ru"))

/**
 * «7 января 2016, 21:15»: the full date with the 24-hour clock in Russian; any other language uses its own long
 * date and short time.
 */
fun formatTakenAt(takenAt: LocalDateTime, locale: Locale): String {
    val formatter = if (locale.language == "ru") {
        RU_FORMATTER
    } else {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale)
    }
    return formatter.format(takenAt)
}
