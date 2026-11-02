package com.kllxx.memorylane.core.domain.language

/**
 * The language of the app as the platform keeps it: the per-app language of Android 13+, which is also in the
 * system settings, and its AndroidX counterpart below. The choice survives restarts without the app storing it,
 * and a change recreates the running activities with the new resources.
 */
interface AppLanguageController {

    /** What is chosen now; [AppLanguage.System] when the app follows the language of the system. */
    fun current(): AppLanguage

    /** Applies [language] at once. Called on the main thread. */
    fun set(language: AppLanguage)
}
