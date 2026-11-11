package io.github.kwyrzyk.puzzlevision.data

import io.github.kwyrzyk.puzzlevision.vision.Candidate
import io.github.kwyrzyk.puzzlevision.vision.Capture
import io.github.kwyrzyk.puzzlevision.vision.FrameTrace
import io.github.kwyrzyk.puzzlevision.vision.GridMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedFrameTest {
    private val metrics =
        GridMetrics(size = 1.2, align = 0.03, tilt = 5.0, skew = 2.0, rotation = 4.0)

    private fun trace(
        metrics: GridMetrics? = this.metrics,
        capture: Capture? = null,
    ) = FrameTrace(
        t = 1234,
        metrics = metrics,
        motion = 0.05,
        colorDrift = 1.5,
        passed = true,
        window = true,
        capture = capture,
    )

    private fun capture(duplicate: Boolean) =
        Capture(1000, 1234, Candidate(emptyList(), 50.0, metrics), emptyList(), duplicate)

    @Test
    fun metricsAreCopied() {
        val frame = savedFrameOf(trace())

        assertEquals(
            SavedFrame(1234, 1.2, 0.03, 5.0, 2.0, 4.0, 0.05, 1.5, window = true, event = null),
            frame,
        )
    }

    @Test
    fun frameWithoutCandidateHasNoMetrics() {
        val frame = savedFrameOf(trace(metrics = null))

        assertEquals(null, frame.size)
        assertEquals(null, frame.tilt)
        assertEquals(0.05, frame.motion!!, 0.0)
    }

    @Test
    fun eventTellsACaptureFromADuplicate() {
        assertEquals(
            FrameEvent.CAPTURE,
            savedFrameOf(trace(capture = capture(duplicate = false))).event,
        )
        assertEquals(
            FrameEvent.DUPLICATE,
            savedFrameOf(trace(capture = capture(duplicate = true))).event,
        )
    }
}
