package com.kllxx.memorylane.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.kllxx.memorylane.core.domain.language.AppLanguage
import com.kllxx.memorylane.core.domain.language.AppLanguageController
import javax.inject.Inject

/**
 * [AppCompatDelegate] is the one API for the per-app language: on Android 13+ it hands the choice to the platform
 * (`LocaleManager`, so it is also in the system settings), below that it stores the choice itself
 * (`AppLocalesMetadataHolderService` in the manifest) and applies it to the activities.
 */
class AppCompatLanguageController @Inject constructor() : AppLanguageController {

    override fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) AppLanguage.System else AppLanguage.fromTag(locales[0]?.toLanguageTag())
    }

    override fun set(language: AppLanguage) {
        val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
