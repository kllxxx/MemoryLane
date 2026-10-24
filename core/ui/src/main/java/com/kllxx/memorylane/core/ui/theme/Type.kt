package com.kllxx.memorylane.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.R

// Bundled variable font (res/font/nunito.ttf). Weight variations apply on API 26+;
// on API 24-25 the default weight is used.
@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: FontWeight) = Font(
    resId = R.font.nunito,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val NunitoFontFamily = FontFamily(
    nunito(FontWeight.Normal),
    nunito(FontWeight.Medium),
    nunito(FontWeight.SemiBold),
    nunito(FontWeight.Bold),
    nunito(FontWeight.ExtraBold),
)

private val Defaults = Typography()

internal val MemoryLaneTypography = Typography(
    displayLarge = Defaults.displayLarge.copy(fontFamily = NunitoFontFamily),
    displayMedium = Defaults.displayMedium.copy(fontFamily = NunitoFontFamily),
    displaySmall = Defaults.displaySmall.copy(fontFamily = NunitoFontFamily),
    headlineLarge = Defaults.headlineLarge.copy(fontFamily = NunitoFontFamily),
    headlineMedium = Defaults.headlineMedium.copy(fontFamily = NunitoFontFamily),
    headlineSmall = Defaults.headlineSmall.copy(fontFamily = NunitoFontFamily),
    titleLarge = Defaults.titleLarge.copy(
        fontFamily = NunitoFontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleMedium = Defaults.titleMedium.copy(
        fontFamily = NunitoFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = Defaults.titleSmall.copy(fontFamily = NunitoFontFamily),
    bodyLarge = Defaults.bodyLarge.copy(
        fontFamily = NunitoFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = Defaults.bodyMedium.copy(fontFamily = NunitoFontFamily, fontSize = 14.sp),
    bodySmall = Defaults.bodySmall.copy(fontFamily = NunitoFontFamily),
    labelLarge = Defaults.labelLarge.copy(fontFamily = NunitoFontFamily, fontSize = 16.sp),
    labelMedium = Defaults.labelMedium.copy(fontFamily = NunitoFontFamily),
    labelSmall = Defaults.labelSmall.copy(fontFamily = NunitoFontFamily),
)

val AppTitleStyle = TextStyle(
    fontFamily = NunitoFontFamily,
    fontSize = 26.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.5.sp,
)
