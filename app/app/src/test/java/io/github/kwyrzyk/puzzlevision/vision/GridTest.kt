package io.github.kwyrzyk.puzzlevision.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GridTest {
    private val tolerance = 1e-6
    private val aside = Sticker(Point(400.0, 150.0), 1600.0, Lab(0.0, 0.0, 0.0))

    private fun metricsOf(stickers: List<Sticker>) = analyzeUpright(stickers).single().metrics

    @Test
    fun fewerThanNineStickersGiveNoCandidate() {
        assertTrue(analyzeUpright(face().drop(1)).isEmpty())
    }

    @Test
    fun idealFaceHasPerfectMetrics() {
        val candidate = analyzeUpright(face()).single()
        assertEquals(50.0, candidate.pitch, tolerance)
        assertEquals(1.0, candidate.metrics.size, tolerance)
        assertEquals(0.0, candidate.metrics.align, tolerance)
        assertEquals(0.0, candidate.metrics.tilt, tolerance)
        assertEquals(0.0, candidate.metrics.skew, tolerance)
        assertEquals(0.0, candidate.metrics.rotation, tolerance)
    }

    @Test
    fun shuffledStickersAreOrderedByRowsAndColumns() {
        val expected = face(rotation = 30.0)
        val candidate = analyze(expected.shuffled(java.util.Random(1))) { 30.0 }.single()
        assertEquals(expected, candidate.stickers)
    }

    @Test
    fun anyDirectionOfTheRectangleGivesTheSameOrder() {
        val expected = face(rotation = 30.0)
        for (angle in listOf(30.0, 120.0, -60.0, 210.0)) {
            assertEquals(expected, analyze(expected) { angle }.single().stickers)
        }
    }

    @Test
    fun rotationIsMeasured() {
        assertEquals(8.0, metricsOf(face(rotation = 8.0)).rotation, tolerance)
    }

    @Test
    fun tiltComesFromShortenedColumns() {
        val shortened = 0.5 // cos 60
        val stickers = face().map { it.copy(center = it.center.copy(y = it.center.y * shortened)) }
        assertEquals(60.0, metricsOf(stickers).tilt, tolerance)
    }

    @Test
    fun skewComesFromSlantedColumns() {
        // x + y keeps the rows horizontal and turns the columns by 45 degrees
        val stickers = face().map { it.copy(center = it.center.copy(x = it.center.x + it.center.y)) }
        assertEquals(45.0, metricsOf(stickers).skew, tolerance)
    }

    @Test
    fun sizeIsLargestOverSmallestArea() {
        val stickers = face().mapIndexed { i, s -> if (i == 0) s.copy(area = 3200.0) else s }
        assertEquals(2.0, metricsOf(stickers).size, tolerance)
    }

    @Test
    fun alignIsDistanceFromTheLineOverPitch() {
        val stickers = face().mapIndexed { i, s ->
            if (i == 1) s.copy(center = s.center.copy(y = s.center.y + 3.0)) else s
        }
        // Of 3 centers with the middle one off by 3, the farthest from the line is 2 away.
        assertEquals(2.0 / 50.0, metricsOf(stickers).align, 1e-3)
    }

    @Test
    fun faceIsChosenOverSetsWithAStickerAside() {
        val candidates = analyzeUpright(face() + aside)
        assertTrue(candidates.size > 1)
        assertFalse(aside in candidates.first().stickers)
    }

    @Test
    fun gateTakesTheFirstCandidateWithinTheLimits() {
        val candidates = analyzeUpright(face() + aside.copy(area = 3200.0))
        assertSame(candidates.first(), gate(candidates, limits(Params(), gate_passed = false)))
    }

    @Test
    fun gateRejectsARotatedFace() {
        val candidates = analyzeUpright(face(rotation = 15.0))
        assertNull(gate(candidates, limits(Params(rotation = 10.0), gate_passed = false)))
    }

    @Test
    fun limitsAreLoosenedOnlyWithinAWindow() {
        val params = Params(sizeRatio = 1.5, align = 0.1, tilt = 20.0, rotation = 10.0, delta = 0.5)
        assertEquals(GridMetrics(1.5, 0.1, 20.0, 20.0, 10.0), limits(params, gate_passed = false))
        val loose = limits(params, gate_passed = true)
        assertEquals(1.75, loose.size, tolerance)
        assertEquals(0.15, loose.align, tolerance)
        assertEquals(30.0, loose.tilt, tolerance)
        assertEquals(30.0, loose.skew, tolerance)
        assertEquals(15.0, loose.rotation, tolerance)
    }
}