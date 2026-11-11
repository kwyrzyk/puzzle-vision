package io.github.kwyrzyk.puzzlevision.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.kwyrzyk.puzzlevision.data.FrameEvent
import io.github.kwyrzyk.puzzlevision.data.SavedFrame
import io.github.kwyrzyk.puzzlevision.vision.Params
import io.github.kwyrzyk.puzzlevision.vision.limits

private val SERIES_COLORS = listOf(Color(0xFF1E88E5), Color(0xFFE53935))
private val CAPTURE_COLOR = Color(0xFF43A047)
private val DUPLICATE_COLOR = Color(0xFFFB8C00)
private val WINDOW_COLOR = Color.Gray.copy(alpha = 0.25f)

private class Series(val name: String, val value: (SavedFrame) -> Double?)

/** One panel; [loose] is the limit within a window, null when there is none */
private class Panel(val series: List<Series>, val strict: Double, val loose: Double?)

/** The panels of notebook 3.3, in its order */
private fun panelsOf(p: Params): List<Panel> {
    val strict = limits(p, gate_passed = false)
    val loose = limits(p, gate_passed = true)
    return listOf(
        Panel(
            listOf(Series("tilt", SavedFrame::tilt), Series("skew", SavedFrame::skew)),
            strict.tilt,
            loose.tilt,
        ),
        Panel(listOf(Series("rotation", SavedFrame::rotation)), strict.rotation, loose.rotation),
        Panel(listOf(Series("align", SavedFrame::align)), strict.align, loose.align),
        Panel(listOf(Series("size", SavedFrame::size)), strict.size, loose.size),
        Panel(listOf(Series("motion", SavedFrame::motion)), p.motion, null),
        Panel(listOf(Series("color", SavedFrame::colorDrift)), p.colorDrift, null),
    )
}

/**
 * Metrics of every frame of a session, as the timeline of notebook 3.3: the limit dashed, the
 * loosened limit within a window dotted, windows shaded, captures green, duplicates orange.
 * Each panel shows 0 to 2 x its limit; larger values stay at the top.
 */
@Composable
fun Timelines(frames: List<SavedFrame>, p: Params, modifier: Modifier = Modifier) {
    if (frames.size < 2) {
        Text("No frames saved", modifier)
        return
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val seconds = (frames.last().t - frames.first().t) / 1000.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (panel in panelsOf(p)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, series) in panel.series.withIndex()) {
                    Text(
                        series.name,
                        color = SERIES_COLORS[k],
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Canvas(Modifier.fillMaxWidth().height(64.dp)) { drawPanel(frames, panel, ink) }
        }
        Text("0 to %.1f s".format(seconds), style = MaterialTheme.typography.bodySmall)
    }
}

private fun DrawScope.drawPanel(frames: List<SavedFrame>, panel: Panel, ink: Color) {
    val start = frames.first().t
    val span = (frames.last().t - start).coerceAtLeast(1).toFloat()
    val top = 2 * panel.strict
    fun x(t: Long) = (t - start) / span * size.width
    fun y(value: Double) = size.height * (1 - (value / top).coerceIn(0.0, 1.0)).toFloat()

    // A frame in a window shades the time until the next frame
    for ((a, b) in frames.zipWithNext()) {
        if (a.window) {
            drawRect(WINDOW_COLOR, Offset(x(a.t), 0f), Size(x(b.t) - x(a.t), size.height))
        }
    }
    for (frame in frames) {
        val color = when (frame.event) {
            FrameEvent.CAPTURE -> CAPTURE_COLOR
            FrameEvent.DUPLICATE -> DUPLICATE_COLOR
            null -> continue
        }
        drawLine(color, Offset(x(frame.t), 0f), Offset(x(frame.t), size.height), 2.dp.toPx())
    }

    fun limit(value: Double, dash: Float) = drawLine(
        ink,
        Offset(0f, y(value)),
        Offset(size.width, y(value)),
        1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
    )
    limit(panel.strict, 6.dp.toPx())
    panel.loose?.takeIf { it != panel.strict }?.let { limit(it, 1.dp.toPx()) }

    for ((k, series) in panel.series.withIndex()) {
        val color = SERIES_COLORS[k]
        // A frame without a value breaks the line
        for ((a, b) in frames.zipWithNext()) {
            val from = series.value(a) ?: continue
            val to = series.value(b) ?: continue
            drawLine(color, Offset(x(a.t), y(from)), Offset(x(b.t), y(to)), 1.dp.toPx())
        }
        for (frame in frames) {
            val value = series.value(frame) ?: continue
            drawCircle(color, 1.5.dp.toPx(), Offset(x(frame.t), y(value)))
        }
    }
    drawRect(ink.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
}
