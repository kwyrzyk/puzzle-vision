package io.github.kwyrzyk.puzzlevision.ui

import io.github.kwyrzyk.puzzlevision.vision.Lab
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionsTest {
    private val lWeight = 0.3

    @Test
    fun whiteAndBlackAreShownAsSuch() {
        val white = displayColorOf(Lab(100 * lWeight, 0.0, 0.0), lWeight)
        val black = displayColorOf(Lab(0.0, 0.0, 0.0), lWeight)

        for (channel in listOf(white.red, white.green, white.blue)) assertEquals(1f, channel, 0.01f)
        for (channel in listOf(black.red, black.green, black.blue)) assertEquals(0f, channel, 0.01f)
    }

    @Test
    fun lightnessIsUnweighted() {
        // Without dividing by l_weight, L 30 would be a dark gray
        val gray = displayColorOf(Lab(50 * lWeight, 0.0, 0.0), lWeight)

        assertEquals(0.47f, gray.red, 0.02f) // L 50 is sRGB 119 of 255
    }

    @Test
    fun redStickerIsRed() {
        val red = displayColorOf(Lab(47.3 * lWeight, 68.6, 45.3), lWeight)

        assertTrue(red.red > 0.6f && red.green < 0.3f && red.blue < 0.3f)
    }

    @Test
    fun valuesOutOfRangeDoNotThrow() {
        displayColorOf(Lab(500.0, 300.0, -300.0), lWeight)
        displayColorOf(Lab(30.0, 0.0, 0.0), lWeight = 0.0)
    }

    @Test
    fun dateIsInTheGivenZone() {
        val ms = 86_400_000L + 3_723_000L // 1 day, 1 h 2 min 3 s

        assertEquals("1970-01-02 01:02:03", dateOf(ms, ZoneOffset.UTC))
        assertEquals("1970-01-02 03:02:03", dateOf(ms, ZoneOffset.ofHours(2)))
    }
}
