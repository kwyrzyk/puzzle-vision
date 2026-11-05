package io.github.kwyrzyk.puzzlevision.vision

/** A held face; colors are the medians of each sticker over the window. Times in ms. */
data class Capture(
    val first: Long,
    val last: Long,
    val candidate: Candidate,
    val colors: List<Lab>,
    val duplicate: Boolean,
)

/** What happened on one frame, for the timelines */
data class FrameTrace(
    val t: Long,
    val metrics: GridMetrics?, // of the candidate with the smallest align
    val motion: Double?,
    val colorDrift: Double?,
    val passed: Boolean,
    val window: Boolean,
    val capture: Capture?,
)

/** Fed frame by frame, one instance per session */
class AutoCapture(private val p: Params) {
    private class Entry(val t: Long, val candidate: Candidate)

    private val strict = limits(p, gate_passed = false)
    private val loose = limits(p, gate_passed = true)
    private val window = mutableListOf<Entry>()
    private val captured = mutableListOf<Capture>() // duplicates are not kept

    fun process(t: Long, candidates: List<Candidate>): FrameTrace {
        var motion: Double? = null
        var colorDrift: Double? = null
        var pick = gate(candidates, if (window.isEmpty()) strict else loose)

        if (window.isNotEmpty() && pick == null && t - window.last().t > p.maxGapMs) {
            window.clear()
        }
        if (window.isNotEmpty() && pick != null) {
            val current: Candidate = pick
            motion = motionOf(current, window.last().candidate)
            colorDrift = colorDriftOf(current, medianColors())
            if (motion <= p.motion && colorDrift <= p.colorDrift) {
                window.add(Entry(t, current))
            } else {
                window.clear()
                pick = gate(candidates, strict)
            }
        }
        if (window.isEmpty() && pick != null) {
            window.add(Entry(t, pick))
        }

        // A frame without a grid does not capture.
        val capture =
            if (pick != null && t - window.first().t >= p.holdMs) closeWindow(t) else null

        return FrameTrace(
            t = t,
            metrics = candidates.firstOrNull()?.metrics,
            motion = motion,
            colorDrift = colorDrift,
            passed = pick != null,
            window = window.isNotEmpty() || capture != null,
            capture = capture,
        )
    }

    private fun closeWindow(t: Long): Capture {
        val colors = medianColors()
        // Jesli ta logika polega na sprawdzenie bliskości centrów to jest to nie dopuszczalne, musi to być warunek na bliskość wszystki naklejek i trzeba dobrać go eksperymentalnie
        val duplicate = captured.any { colors[CENTER].distanceTo(it.colors[CENTER]) < p.dedup }
        val capture = Capture(window.first().t, t, window.last().candidate, colors, duplicate)
        if (!duplicate) captured.add(capture)
        window.clear()
        return capture
    }

    private fun motionOf(current: Candidate, previous: Candidate): Double =
        current.points.zip(previous.points) { a, b -> a.distanceTo(b) }.max() / current.pitch

    private fun colorDriftOf(current: Candidate, median: List<Lab>): Double =
        current.colors.zip(median) { a, b -> a.distanceTo(b) }.max()

    private fun medianColors(): List<Lab> {
        val colors = window.map { it.candidate.colors }
        return (0 until STICKERS).map { i ->
            Lab(
                l = median(colors.map { it[i].l }),
                a = median(colors.map { it[i].a }),
                b = median(colors.map { it[i].b }),
            )
        }
    }

    private companion object {
        const val STICKERS = 9
        const val CENTER = 4
    }
}

private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}
