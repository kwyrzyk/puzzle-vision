package io.github.kwyrzyk.puzzlevision.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureTest {
    private val red = Lab(30.0, 50.0, 40.0)
    private val blue = Lab(20.0, 10.0, -50.0)
    private val params = Params(holdMs = 300, maxGapMs = 200, delta = 0.0)

    /** One frame for each time; null is a frame without stickers. */
    private fun AutoCapture.run(vararg frames: Pair<Long, List<Sticker>?>) =
        frames.map { (t, stickers) -> process(t, analyzeUpright(stickers.orEmpty())) }

    @Test
    fun faceHeldForHoldMsIsCaptured() {
        val traces = AutoCapture(params).run(
            0L to face(color = red),
            100L to face(color = red),
            200L to face(color = red),
            300L to face(color = red),
        )
        assertEquals(listOf(true, true, true, true), traces.map { it.window })
        assertNull(traces[2].capture)
        val capture = traces[3].capture!!
        assertEquals(0L, capture.first)
        assertEquals(300L, capture.last)
        assertFalse(capture.duplicate)
    }

    @Test
    fun colorsAreMediansOverTheWindow() {
        val capture = AutoCapture(params).run(
            0L to face(color = Lab(10.0, 0.0, 0.0)),
            100L to face(color = Lab(12.0, 0.0, 0.0)),
            200L to face(color = Lab(11.0, 0.0, 0.0)),
            300L to face(color = Lab(13.0, 0.0, 0.0)),
        ).last().capture!!
        assertEquals(11.5, capture.colors[4].l, 1e-9)
    }

    @Test
    fun windowCloses_nextFrameOpensANewOne() {
        val traces = AutoCapture(params).run(
            0L to face(color = red),
            300L to face(color = red),
            400L to face(color = red),
        )
        assertNotNull(traces[1].capture)
        assertTrue(traces[2].window)
        assertNull(traces[2].capture)
    }

    @Test
    fun noWindowWithoutAGrid() {
        val trace = AutoCapture(params).process(0L, emptyList())
        assertFalse(trace.passed)
        assertFalse(trace.window)
        assertNull(trace.metrics)
    }

    @Test
    fun gapWithinMaxGapKeepsTheWindow() {
        val traces = AutoCapture(params).run(
            0L to face(color = red),
            100L to null,
            200L to null,
            300L to face(color = red),
        )
        assertEquals(listOf(true, true, true, true), traces.map { it.window })
        assertEquals(0L, traces[3].capture!!.first)
    }

    @Test
    fun gapLongerThanMaxGapDropsTheWindow() {
        val traces = AutoCapture(params).run(
            0L to face(color = red),
            100L to null,
            200L to null,
            300L to null,
            400L to face(color = red),
        )
        assertFalse(traces[3].window)
        assertTrue(traces[4].window)
        assertNull(traces[4].capture)
    }

    @Test
    fun frameWithoutAGridDoesNotCapture() {
        val capture = AutoCapture(params.copy(holdMs = 100))
        val traces = capture.run(0L to face(color = red), 100L to null)
        assertNull(traces[1].capture)
        assertTrue(traces[1].window)
    }

    @Test
    fun motionDropsTheWindowAndOpensANewOne() {
        val moved = Point(160.0, 150.0) // 10 px of the pitch 50: 0.2, the limit is 0.1
        val traces = AutoCapture(params).run(
            0L to face(color = red),
            100L to face(color = red),
            200L to face(color = red, center = moved),
            300L to face(color = red, center = moved),
            500L to face(color = red, center = moved),
        )
        assertEquals(0.2, traces[2].motion!!, 1e-9)
        assertTrue(traces[2].window)
        assertNull(traces[3].capture) // 100 ms since the new window opened
        assertEquals(200L, traces[4].capture!!.first)
    }

    @Test
    fun colorChangeDropsTheWindow() {
        val traces = AutoCapture(params).run(
            0L to face(color = red),
            100L to face(color = red),
            200L to face(color = blue),
            300L to face(color = blue),
        )
        assertTrue(traces[2].colorDrift!! > params.colorDrift)
        assertNull(traces[3].capture) // the 300 ms count restarted at 200 ms
    }

    @Test
    fun sameCenterColorIsADuplicate() {
        val capture = AutoCapture(params)
        val first = capture.run(0L to face(color = red), 300L to face(color = red))
        val again = capture.run(400L to face(color = red), 700L to face(color = red))
        val other = capture.run(800L to face(color = blue), 1100L to face(color = blue))
        assertFalse(first.last().capture!!.duplicate)
        assertTrue(again.last().capture!!.duplicate)
        assertFalse(other.last().capture!!.duplicate)
    }

    @Test
    fun deltaKeepsAWindowOnTheBorderOfALimit() {
        val frames = arrayOf(
            0L to face(rotation = 9.0, color = red),
            100L to face(rotation = 11.0, color = red),
            200L to face(rotation = 11.0, color = red),
        )
        val strict = AutoCapture(params.copy(rotation = 10.0, delta = 0.0, maxGapMs = 0))
        val loose = AutoCapture(params.copy(rotation = 10.0, delta = 0.3, maxGapMs = 0))
        assertFalse(strict.run(*frames).last().window)
        assertTrue(loose.run(*frames).last().window)
    }

    @Test
    fun deltaDoesNotOpenAWindow() {
        val capture = AutoCapture(params.copy(rotation = 10.0, delta = 0.3))
        assertFalse(capture.process(0L, analyzeUpright(face(rotation = 11.0))).window)
    }
}