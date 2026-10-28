package com.kllxx.memorylane.core.photos.data.geocoder

/**
 * "City, Country": the city is the [locality], else the [subAdminArea], else the [adminArea]. A part the geocoder
 * does not know is left out, so a photo with only a country reads "Country". Null when nothing is known.
 * A city that has the name of its country, like Singapore, is written once.
 */
internal fun formatPlace(
    locality: String?,
    subAdminArea: String?,
    adminArea: String?,
    countryName: String?,
): String? {
    val place = firstNotBlank(locality, subAdminArea, adminArea)
    val country = firstNotBlank(countryName)
    return listOfNotNull(place, country).distinct().takeIf { it.isNotEmpty() }?.joinToString(", ")
}

private fun firstNotBlank(vararg values: String?): String? =
    values.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
