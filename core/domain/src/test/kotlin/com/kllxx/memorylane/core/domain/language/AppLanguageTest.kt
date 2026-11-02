package com.kllxx.memorylane.core.domain.language

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `a stored tag gives its language`() {
        assertEquals(AppLanguage.Russian, AppLanguage.fromTag("ru"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("en"))
    }

    @Test
    fun `a tag with a region gives the language of the app`() {
        assertEquals(AppLanguage.Russian, AppLanguage.fromTag("ru-RU"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("en-GB"))
    }

    @Test
    fun `no tag and a language the app is not translated into follow the system`() {
        assertEquals(AppLanguage.System, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.System, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.System, AppLanguage.fromTag("de"))
    }

    @Test
    fun `every language has its own tag and the system has none`() {
        assertEquals(null, AppLanguage.System.tag)
        assertEquals(listOf("ru", "en"), AppLanguage.entries.mapNotNull { it.tag })
    }
}
