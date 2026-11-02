package com.kllxx.memorylane.core.domain.language

/** The language of the app: the language of the system, or one of the two the app is translated into. */
enum class AppLanguage(
    /** The BCP 47 tag of the language, null for [System]. */
    val tag: String?,
) {
    System(null),
    Russian("ru"),
    English("en"),
    ;

    companion object {
        /** The language for a stored tag; a language the app is not translated into follows the system. */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.substringBefore('-')?.lowercase()
            return entries.firstOrNull { it.tag != null && it.tag == language } ?: System
        }
    }
}
