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

    // --- the card behind ---

    private val landscapeTop = CardSize(344f, 258f)
    private val portraitOwn = CardSize(344f, 459f)

    @Test
    fun `the drag progress follows the scale the library gives the card behind`() {
        assertEquals(0f, behindProgress(0.8f), 0f)
        assertEquals(0.5f, behindProgress(0.9f), 0.0001f)
        assertEquals(1f, behindProgress(1f), 0f)
    }

    @Test
    fun `the progress stays between 0 and 1 whatever the library reports`() {
        assertEquals(0f, behindProgress(0f), 0f)
        assertEquals(0f, behindProgress(-1f), 0f)
        assertEquals(1f, behindProgress(1.4f), 0f)
    }

    @Test
    fun `at rest the card behind is the top card times 0_94, whatever its own shape`() {
        val size = behindSize(landscapeTop, portraitOwn, 0f)

        assertEquals(landscapeTop.width * BEHIND_SCALE, size.width, 0.001f)
        assertEquals(landscapeTop.height * BEHIND_SCALE, size.height, 0.001f)
    }

    @Test
    fun `once the top card is dragged away the card behind has its own size`() {
        assertEquals(portraitOwn, behindSize(landscapeTop, portraitOwn, 1f))
    }

    @Test
    fun `half way the card behind is half way between the two sizes`() {
        val size = behindSize(landscapeTop, portraitOwn, 0.5f)

        assertEquals((landscapeTop.width * BEHIND_SCALE + portraitOwn.width) / 2f, size.width, 0.001f)
        assertEquals((landscapeTop.height * BEHIND_SCALE + portraitOwn.height) / 2f, size.height, 0.001f)
    }

    @Test
    fun `the size of the card behind grows steadily with the drag`() {
        val sizes = (0..10).map { behindSize(landscapeTop, portraitOwn, it / 10f) }

        sizes.zipWithNext { a, b ->
            assertTrue(b.height >= a.height)
            assertTrue(b.width >= a.width)
        }
    }

    @Test
    fun `a progress outside 0 and 1 does not take the card outside its two sizes`() {
        assertEquals(behindSize(landscapeTop, portraitOwn, 0f), behindSize(landscapeTop, portraitOwn, -3f))
        assertEquals(portraitOwn, behindSize(landscapeTop, portraitOwn, 3f))
    }

    @Test
    fun `at rest the top edge of the card behind is 10 above the top card, whatever the card height`() {
        listOf(100f, 240f, 258f, 427f, 459f, 490f).forEach { height ->
            val top = CardSize(300f, height)
            // Scaled about its centre the card is shorter by 0.06 of its height, so its top edge is 0.03 lower; then the shift
            val topEdge = -height * BEHIND_SCALE / 2f - behindShift(top, 0f)
            assertEquals("height $height", -height / 2f - BEHIND_PEEK, topEdge, 0.001f)
        }
    }

    @Test
    fun `the card behind peeks above a tall top card too`() {
        // A shift of 10 alone would leave the edge below a tall card: 0.03 of 490 is 14.7
        assertTrue(behindShift(CardSize(300f, 490f), 0f) > 14.7f)
    }

    @Test
    fun `the shift goes to 0 as the card takes the place of the top card`() {
        assertEquals(0f, behindShift(landscapeTop, 1f), 0f)
        assertTrue(behindShift(landscapeTop, 0.5f) < behindShift(landscapeTop, 0f))
        assertTrue(behindShift(landscapeTop, 0.5f) > 0f)
    }

    @Test
    fun `a photo whose shape is not known yet is shown as a portrait one`() {
        assertEquals(3f / 4f, PROVISIONAL_ASPECT_RATIO, 0f)
    }
}
