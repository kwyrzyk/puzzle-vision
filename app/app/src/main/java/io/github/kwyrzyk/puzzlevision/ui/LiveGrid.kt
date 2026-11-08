package io.github.kwyrzyk.puzzlevision.ui

import io.github.kwyrzyk.puzzlevision.vision.Frame
import io.github.kwyrzyk.puzzlevision.vision.FrameTrace
import io.github.kwyrzyk.puzzlevision.vision.Params
import io.github.kwyrzyk.puzzlevision.vision.Point
import io.github.kwyrzyk.puzzlevision.vision.gate
import io.github.kwyrzyk.puzzlevision.vision.limits

/**
 * What is drawn over the preview; coordinates in the segmentation square of [width] px.
 *
 * [grid] is the 9 centers by rows: the grid that passed the gate, else the best one.
 */
data class LiveGrid(
    val width: Int,
    val stickers: List<Point>,
    val grid: List<Point>?,
    val passed: Boolean,
)

/** [trace] is null when no session runs; within a window the loosened limits apply */
fun liveGridOf(frame: Frame, trace: FrameTrace?, p: Params): LiveGrid {
    val pick = gate(frame.candidates, limits(p, gate_passed = trace?.window == true))
    return LiveGrid(
        width = p.width,
        stickers = frame.stickers.map { it.center },
        grid = (pick ?: frame.candidates.firstOrNull())?.points,
        passed = pick != null,
    )
}