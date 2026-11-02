package com.kllxx.memorylane.core.domain.format

import java.time.LocalDate
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

private fun ruDate(): DateTimeFormatterBuilder = DateTimeFormatterBuilder()
    .appendValue(ChronoField.DAY_OF_MONTH)
    .appendLiteral(' ')
    .appendText(ChronoField.MONTH_OF_YEAR, RU_MONTHS.withIndex().associate { (index, name) -> (index + 1L) to name })
    .appendPattern(" yyyy")

private fun ruDayMonth(): DateTimeFormatterBuilder = DateTimeFormatterBuilder()
    .appendValue(ChronoField.DAY_OF_MONTH)
    .appendLiteral(' ')
    .appendText(ChronoField.MONTH_OF_YEAR, RU_MONTHS.withIndex().associate { (index, name) -> (index + 1L) to name })

private val RU_LOCALE = Locale.forLanguageTag("ru")
private val RU_DATE_TIME: DateTimeFormatter = ruDate().appendPattern(", HH:mm").toFormatter(RU_LOCALE)
private val RU_DATE: DateTimeFormatter = ruDate().toFormatter(RU_LOCALE)
private val RU_DAY_MONTH: DateTimeFormatter = ruDayMonth().toFormatter(RU_LOCALE)

/** English reads day first, «7 January 2016, 21:15», with the 24-hour clock; the platform default would be «January 7, 2016 at 9:15 PM». */
private val EN_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH)
private val EN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
private val EN_DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)

/**
 * «7 января 2016, 21:15» in Russian, «7 January 2016, 21:15» in English: the full date with the 24-hour clock; any
 * other language uses its own long date and short time. A photo that has only a day ([hasTime] false) shows just the date, «7 января 2016»: its time
 * is a placeholder. A real midnight has [hasTime] true and is shown as «00:00».
 */
fun formatTakenAt(takenAt: LocalDateTime, locale: Locale, hasTime: Boolean = true): String {
    val formatter = when {
        locale.language == "ru" -> if (hasTime) RU_DATE_TIME else RU_DATE
        locale.language == "en" -> if (hasTime) EN_DATE_TIME else EN_DATE
        hasTime -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    }
    return formatter.format(takenAt)
}

/** «28 октября» in Russian (the genitive), «28 October» in English, the locale's own day and month otherwise. */
fun formatDayMonth(date: LocalDate, locale: Locale): String {
    val formatter = when (locale.language) {
        "ru" -> RU_DAY_MONTH
        "en" -> EN_DAY_MONTH
        else -> DateTimeFormatter.ofPattern("d MMMM", locale)
    }
    return formatter.format(date)
}
