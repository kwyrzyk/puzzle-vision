package io.github.kwyrzyk.puzzlevision.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParamsTest {
    @Test
    fun defaultsAreValid() {
        assertNull(Params().validate())
    }

    @Test
    fun rejectsValueOutOfRange() {
        assertEquals("width must be in 100..1080", Params(width = 50).validate())
    }

    @Test
    fun rejectsEvenBlur() {
        assertEquals("blur must be odd", Params(blur = 4).validate())
    }

    @Test
    fun rejectsStickerMinNotBelowMax() {
        assertEquals(
            "sticker_min must be below sticker_max",
            Params(stickerMin = 0.3, stickerMax = 0.3).validate()
        )
    }

    @Test
    fun boxesMustBeNested() {
        assertNull(Params(showBox = 0.9, acceptBox = 0.9).validate())
        assertEquals(
            "show_box must not exceed accept_box",
            Params(showBox = 0.95, acceptBox = 0.9).validate(),
        )
        assertEquals(
            "accept_box must be below segment_box",
            Params(acceptBox = 1.0, segmentBox = 1.0).validate(),
        )
    }
}