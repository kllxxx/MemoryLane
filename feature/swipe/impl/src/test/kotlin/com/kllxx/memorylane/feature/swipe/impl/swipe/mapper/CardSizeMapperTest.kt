package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSizeMapperTest {

    // The photo area of the design: 360 by 500, less 20 on every side
    private val areaWidth = 320f
    private val areaHeight = 460f

    @Test
    fun `a portrait photo gives a tall card as wide as the area allows`() {
        val card = fitInside(areaWidth, areaHeight, aspectRatio = 3f / 4f)

        // 320 wide would need 426.7 high, which fits in 460
        assertEquals(320f, card.width, 0.001f)
        assertEquals(426.667f, card.height, 0.01f)
    }

    @Test
    fun `a landscape photo gives a wide, short card`() {
        val card = fitInside(areaWidth, areaHeight, aspectRatio = 4f / 3f)

        assertEquals(320f, card.width, 0.001f)
        assertEquals(240f, card.height, 0.001f)
    }

    @Test
    fun `a very tall photo is limited by the height of the area`() {
        val card = fitInside(areaWidth, areaHeight, aspectRatio = 1f / 3f)

        assertEquals(460f, card.height, 0.001f)
        assertEquals(153.333f, card.width, 0.01f)
    }

    @Test
    fun `a very wide photo is limited by the width of the area`() {
        val card = fitInside(areaWidth, areaHeight, aspectRatio = 3f)

        assertEquals(320f, card.width, 0.001f)
        assertEquals(106.667f, card.height, 0.01f)
    }

    @Test
    fun `a square photo is as large as the narrower side`() {
        val card = fitInside(areaWidth, areaHeight, aspectRatio = 1f)

        assertEquals(320f, card.width, 0.001f)
        assertEquals(320f, card.height, 0.001f)
    }

    @Test
    fun `the card never gets bigger than the area`() {
        listOf(0.2f, 0.5f, 0.75f, 1f, 1.33f, 1.78f, 4f).forEach { ratio ->
            val card = fitInside(areaWidth, areaHeight, ratio)
            assertTrue("width at $ratio", card.width <= areaWidth + 0.001f)
            assertTrue("height at $ratio", card.height <= areaHeight + 0.001f)
        }
    }

    @Test
    fun `the card keeps the aspect ratio it was asked for`() {
        listOf(0.2f, 0.5f, 0.75f, 1f, 1.33f, 1.78f, 4f).forEach { ratio ->
            val card = fitInside(areaWidth, areaHeight, ratio)
            assertEquals("ratio $ratio", ratio, card.width / card.height, 0.0001f)
        }
    }

    @Test
    fun `the card is as large as possible, touching the area on at least one axis`() {
        listOf(0.2f, 0.75f, 1.33f, 4f).forEach { ratio ->
            val card = fitInside(areaWidth, areaHeight, ratio)
            val touches = card.width >= areaWidth - 0.001f || card.height >= areaHeight - 0.001f
            assertTrue("ratio $ratio", touches)
        }
    }

    @Test
    fun `no room gives no card`() {
        assertEquals(CardSize.Zero, fitInside(0f, 460f, 0.75f))
        assertEquals(CardSize.Zero, fitInside(320f, 0f, 0.75f))
        assertEquals(CardSize.Zero, fitInside(-5f, -5f, 0.75f))
    }

    @Test
    fun `an unusable aspect ratio gives no card`() {
        assertEquals(CardSize.Zero, fitInside(320f, 460f, 0f))
        assertEquals(CardSize.Zero, fitInside(320f, 460f, -1f))
        assertEquals(CardSize.Zero, fitInside(320f, 460f, Float.NaN))
    }

    @Test
    fun `the card behind has its top edge 14 below the top card, as in the design`() {
        // Portrait card of 427 high: scaled to 0.94 about its centre the top edge is 12.81 lower, so 1.19 is left to add
        assertEquals(1.19f, behindCenterShift(427f), 0.001f)
        // Landscape card of 240 high: 7.2 from the scaling, 6.8 to add
        assertEquals(6.8f, behindCenterShift(240f), 0.001f)
    }

    @Test
    fun `the top edge of the card behind is always 14 lower, whatever the card height`() {
        listOf(100f, 240f, 427f, 460f).forEach { height ->
            val shrunkTop = (height - height * BEHIND_SCALE) / 2f
            assertEquals("height $height", 14f, shrunkTop + behindCenterShift(height), 0.001f)
        }
    }

    @Test
    fun `a photo whose shape is not known yet is shown as a portrait one`() {
        assertEquals(3f / 4f, PROVISIONAL_ASPECT_RATIO, 0f)
    }
}
