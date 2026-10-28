package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import org.junit.Assert.assertEquals
import org.junit.Test

class FontScaleMapperTest {

    // The system multiplies the result by the font scale, so what is shown is result * fontScale
    private fun shownScale(baseSp: Float, fontScale: Float) = limitedFontSp(baseSp, fontScale) * fontScale / baseSp

    @Test
    fun `at the normal font size the text keeps its size`() {
        assertEquals(11f, limitedFontSp(11f, fontScale = 1f), 0.0001f)
    }

    @Test
    fun `up to the limit the text follows the font scale`() {
        assertEquals(1.15f, shownScale(11f, 1.15f), 0.0001f)
        assertEquals(1.3f, shownScale(11f, 1.3f), 0.0001f)
        assertEquals(11f, limitedFontSp(11f, fontScale = 1.3f), 0.0001f)
    }

    @Test
    fun `above the limit the text stops growing`() {
        assertEquals(1.3f, shownScale(11f, 1.5f), 0.0001f)
        assertEquals(1.3f, shownScale(11f, 2f), 0.0001f)
        assertEquals(1.3f, shownScale(11f, 3f), 0.0001f)
    }

    @Test
    fun `a small font scale is followed down too`() {
        assertEquals(0.85f, shownScale(11f, 0.85f), 0.0001f)
    }

    @Test
    fun `a line height scales the same way as the text`() {
        assertEquals(1.3f, shownScale(15f, 2f), 0.0001f)
    }

    @Test
    fun `a custom limit is used`() {
        assertEquals(1.1f, limitedFontSp(10f, fontScale = 2f, maxScale = 1.1f) * 2f / 10f, 0.0001f)
    }

    @Test
    fun `a font scale of zero does not divide by zero`() {
        assertEquals(11f, limitedFontSp(11f, fontScale = 0f), 0f)
    }
}
