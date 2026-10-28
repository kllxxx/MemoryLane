package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import com.haroncode.lazycardstack.swiper.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class DragMapperTest {

    private val width = 1_000f

    @Test
    fun `a card at rest shows nothing`() {
        assertEquals(DragOverlay.None, dragOverlay(0f, width))
    }

    @Test
    fun `dragging left is the delete side and dragging right is the keep side`() {
        assertEquals(SwipeDirection.Left, dragOverlay(-100f, width).direction)
        assertEquals(SwipeDirection.Right, dragOverlay(100f, width).direction)
    }

    @Test
    fun `the tint grows with the drag up to 22 percent`() {
        // The swipe threshold is 40 percent of the width, so 200 is half way
        assertEquals(0.11f, dragOverlay(200f, width).tintAlpha, 0.0001f)
        assertEquals(0.22f, dragOverlay(400f, width).tintAlpha, 0.0001f)
    }

    @Test
    fun `the tint never gets stronger than 22 percent`() {
        assertEquals(MAX_TINT_ALPHA, dragOverlay(5_000f, width).tintAlpha, 0f)
        assertEquals(MAX_TINT_ALPHA, dragOverlay(-5_000f, width).tintAlpha, 0f)
    }

    @Test
    fun `the badge fades in over the same distance`() {
        assertEquals(0.5f, dragOverlay(200f, width).badgeAlpha, 0.0001f)
        assertEquals(1f, dragOverlay(400f, width).badgeAlpha, 0f)
        assertEquals(1f, dragOverlay(-900f, width).badgeAlpha, 0f)
    }

    @Test
    fun `left and right of the same distance look the same`() {
        assertEquals(dragOverlay(150f, width).tintAlpha, dragOverlay(-150f, width).tintAlpha, 0f)
    }

    @Test
    fun `a card of no width shows nothing`() {
        assertEquals(DragOverlay.None, dragOverlay(100f, 0f))
    }
}
