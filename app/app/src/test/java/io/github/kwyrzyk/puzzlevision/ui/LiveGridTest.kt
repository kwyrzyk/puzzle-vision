package io.github.kwyrzyk.puzzlevision.ui

import io.github.kwyrzyk.puzzlevision.vision.Frame
import io.github.kwyrzyk.puzzlevision.vision.FrameTrace
import io.github.kwyrzyk.puzzlevision.vision.Params
import io.github.kwyrzyk.puzzlevision.vision.Sticker
import io.github.kwyrzyk.puzzlevision.vision.analyze
import io.github.kwyrzyk.puzzlevision.vision.face
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveGridTest {
    private val params = Params(delta = 0.5) // rotation limit 10, within a window 15

    private fun frameOf(stickers: List<Sticker>, degrees: Double = 0.0) =
        Frame(stickers, analyze(stickers) { degrees })

    private val inWindow = FrameTrace(
        t = 0, metrics = null, motion = null, colorDrift = null,
        passed = true, window = true, capture = null,
    )

    @Test
    fun gridThatPassesIsDrawnAsPassed() {
        val stickers = face()

        val liveGrid = liveGridOf(frameOf(stickers), trace = null, params)

        assertTrue(liveGrid.passed)
        assertEquals(stickers.map { it.center }, liveGrid.grid)
        assertEquals(stickers.map { it.center }, liveGrid.stickers)
        assertEquals(params.width, liveGrid.width)
    }

    @Test
    fun gridThatFailsIsStillDrawn() {
        val stickers = face(rotation = 30.0)

        val liveGrid = liveGridOf(frameOf(stickers, 30.0), trace = null, params)

        assertFalse(liveGrid.passed)
        assertEquals(9, liveGrid.grid!!.size)
    }

    @Test
    fun withoutCandidatesOnlyTheStickersAreDrawn() {
        val stickers = face().take(5)

        val liveGrid = liveGridOf(frameOf(stickers), trace = null, params)

        assertNull(liveGrid.grid)
        assertFalse(liveGrid.passed)
        assertEquals(5, liveGrid.stickers.size)
    }

    @Test
    fun withinAWindowTheLoosenedLimitsApply() {
        val frame = frameOf(face(rotation = 12.0), 12.0)

        assertFalse(liveGridOf(frame, trace = null, params).passed)
        assertTrue(liveGridOf(frame, inWindow, params).passed)
    }
}
