package io.github.kwyrzyk.puzzlevision.vision

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class Point(val x: Double, val y: Double) {
    fun distanceTo(other: Point): Double = hypot(x - other.x, y - other.y)
}

/** Lab color with L multiplied by `l_weight`. */
@Serializable
data class Lab(val l: Double, val a: Double, val b: Double) {
    fun distanceTo(other: Lab): Double {
        val dl = l - other.l
        val da = a - other.a
        val db = b - other.b
        return sqrt(dl * dl + da * da + db * db)
    }
}

data class Sticker(val center: Point, val area: Double, val color: Lab)

/** Metrics of a grid, or the limits they are compared with; angles in degrees */
data class GridMetrics(
    val size: Double,
    val align: Double,
    val tilt: Double,
    val skew: Double,
    val rotation: Double,
) {
    fun within(limit: GridMetrics): Boolean =
        size <= limit.size &&
                align <= limit.align &&
                tilt <= limit.tilt &&
                skew <= limit.skew &&
                rotation <= limit.rotation
}

/** 9 stickers: rows top to bottom, each row left to right */
data class Candidate(
    val stickers: List<Sticker>,
    val pitch: Double,
    val metrics: GridMetrics,
) {
    val points: List<Point> get() = stickers.map { it.center }
    val colors: List<Lab> get() = stickers.map { it.color }
}

/**
 * Candidates of a frame, sorted by align.
 *
 * [angleOf] gives the angle in degrees of the smallest rectangle around the points,
 * in any of its 4 directions
 */
fun analyze(stickers: List<Sticker>, angleOf: (List<Point>) -> Double): List<Candidate> =
    candidateSets(stickers.map { it.center })
        .map { members -> candidate(members.map { stickers[it] }, angleOf) }
        .sortedBy { it.metrics.align }

/** The first candidate within the limits */
fun gate(candidates: List<Candidate>, limit: GridMetrics): Candidate? =
    candidates.firstOrNull { it.metrics.within(limit) }

/** Limits of the gate; within a window they are loosened by `delta`. */
fun limits(p: Params, gate_passed: Boolean): GridMetrics {
    val f = if (gate_passed) 1 + p.delta else 1.0
    return GridMetrics(
        size = 1 + (p.sizeRatio - 1) * f,
        align = p.align * f,
        tilt = p.tilt * f,
        skew = p.tilt * f,
        rotation = p.rotation * f,
    )
}

private fun candidate(members: List<Sticker>, angleOf: (List<Point>) -> Double): Candidate {
    val order = gridOrderOf(members.map { it.center }, angleOf)
    val stickers = order.map { members[it] }
    val (pitch, metrics) = gridMetrics(stickers.map { it.center }, stickers.map { it.area })
    return Candidate(stickers, pitch, metrics)
}

/** Each sticker with its 8 nearest is a set of 9 indices; equal sets are kept once. */
private fun candidateSets(centers: List<Point>): List<List<Int>> {
    if (centers.size < 9) return emptyList()
    return centers
        .map { center ->
            centers.indices.sortedBy { center.distanceTo(centers[it]) }.take(9).sorted()
        }
        .distinct()
}

/** Indices of the centers by rows, after rotating them upright */
private fun gridOrderOf(centers: List<Point>, angleOf: (List<Point>) -> Double): List<Int> {
    val angle = Math.toRadians(angleOf(centers))
    val theta = atan2(sin(4 * angle), cos(4 * angle)) / 4 // to (-45, 45] degrees: rows stay rows
    val c = cos(-theta)
    val s = sin(-theta)
    val rotated = centers.map { Point(c * it.x - s * it.y, s * it.x + c * it.y) }
    return centers.indices
        .sortedBy { rotated[it].y }
        .chunked(3)
        .flatMap { row -> row.sortedBy { rotated[it].x } }
}

private class Line(val direction: Point, val distance: Double)

/** Total least squares line; direction positive along x or y, largest distance from the line */
private fun lineFit(points: List<Point>, positiveAlongX: Boolean): Line {
    val meanX = points.map { it.x }.average()
    val meanY = points.map { it.y }.average()
    val xx = points.sumOf { (it.x - meanX) * (it.x - meanX) }
    val yy = points.sumOf { (it.y - meanY) * (it.y - meanY) }
    val xy = points.sumOf { (it.x - meanX) * (it.y - meanY) }
    val angle = atan2(2 * xy, xx - yy) / 2
    val dx = cos(angle)
    val dy = sin(angle)
    val distance = points.maxOf { abs((it.y - meanY) * dx - (it.x - meanX) * dy) }
    val positive = if (positiveAlongX) dx >= 0 else dy >= 0
    return Line(if (positive) Point(dx, dy) else Point(-dx, -dy), distance)
}

private fun gridMetrics(points: List<Point>, areas: List<Double>): Pair<Double, GridMetrics> {
    val rows = (0..2).map { r -> points.subList(3 * r, 3 * r + 3) }
    val columns = (0..2).map { c -> listOf(points[c], points[c + 3], points[c + 6]) }
    val rowLines = rows.map { lineFit(it, positiveAlongX = true) }
    val columnLines = columns.map { lineFit(it, positiveAlongX = false) }
    val rowDirection = meanOf(rowLines.map { it.direction })
    val columnDirection = meanOf(columnLines.map { it.direction })

    val horizontal = meanStep(rows)
    val vertical = meanStep(columns)
    val pitch = (horizontal + vertical) / 2

    val cosine = (rowDirection.x * columnDirection.x + rowDirection.y * columnDirection.y) /
            hypot(rowDirection.x, rowDirection.y) /
            hypot(columnDirection.x, columnDirection.y)
    val between = Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))

    return pitch to GridMetrics(
        size = areas.max() / areas.min(),
        align = (rowLines + columnLines).maxOf { it.distance } / pitch,
        tilt = Math.toDegrees(acos(minOf(horizontal, vertical) / maxOf(horizontal, vertical))),
        skew = abs(between - 90),
        rotation = abs(Math.toDegrees(atan2(rowDirection.y, rowDirection.x)))
    )
}

private fun meanOf(points: List<Point>) =
    Point(points.map { it.x }.average(), points.map { it.y }.average())

/** Mean distance of neighboring points of the lines. */
private fun meanStep(lines: List<List<Point>>): Double =
    lines.flatMap { line -> line.zipWithNext { a, b -> a.distanceTo(b) } }.average()