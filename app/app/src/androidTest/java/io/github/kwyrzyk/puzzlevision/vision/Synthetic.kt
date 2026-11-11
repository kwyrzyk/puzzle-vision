package io.github.kwyrzyk.puzzlevision.vision

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.cos
import kotlin.math.sin
import org.opencv.core.Point as CvPoint

val WHITE = Scalar(255.0, 255.0, 255.0)
val YELLOW = Scalar(255.0, 213.0, 0.0)
val RED = Scalar(183.0, 18.0, 52.0)
val ORANGE = Scalar(255.0, 88.0, 0.0)
val BLUE = Scalar(0.0, 70.0, 173.0)
val GREEN = Scalar(0.0, 155.0, 72.0)

/** Rows top to bottom, each left to right */
val FACE = listOf(WHITE, YELLOW, RED, ORANGE, BLUE, GREEN, RED, WHITE, YELLOW)

/** A drawn face and the centers of its stickers in the order of [FACE] */
class Synthetic(val rgb: Mat, val centers: List<Point>)

/**
 * A 3 x 3 face of square stickers on a dark background, RGB.
 *
 * Background 40 is too close to blue for Canny (`canny_low` 40); 15 is not.
 */
fun syntheticFace(
    size: Int = 300,
    center: Point = Point(size / 2.0, size / 2.0),
    side: Double = 40.0,
    gap: Double = 10.0,
    degrees: Double = 0.0,
    colors: List<Scalar> = FACE,
    background: Double = 15.0,
): Synthetic {
    val rgb = Mat(size, size, CvType.CV_8UC3, Scalar(background, background, background))
    val angle = Math.toRadians(degrees)
    fun rotated(dx: Double, dy: Double) = Point(
        center.x + dx * cos(angle) - dy * sin(angle),
        center.y + dx * sin(angle) + dy * cos(angle),
    )

    val pitch = side + gap
    val centers = mutableListOf<Point>()
    for (row in -1..1) for (column in -1..1) {
        val dx = column * pitch
        val dy = row * pitch
        val corners = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (sx, sy) ->
            rotated(dx + sx * side / 2, dy + sy * side / 2).let { CvPoint(it.x, it.y) }
        }
        val polygon = MatOfPoint(*corners.toTypedArray())
        Imgproc.fillConvexPoly(rgb, polygon, colors[centers.size])
        polygon.release()
        centers += rotated(dx, dy)
    }
    return Synthetic(rgb, centers)
}

/** Lab of an RGB color as the detection computes it */
fun labOf(color: Scalar, p: Params): Lab {
    val pixel = Mat(1, 1, CvType.CV_8UC3, color)
    val lab = Mat()
    pixel.convertTo(lab, CvType.CV_32FC3, 1.0 / 255)
    Imgproc.cvtColor(lab, lab, Imgproc.COLOR_RGB2Lab)
    val (l, a, b) = lab.get(0, 0).toList()
    pixel.release()
    lab.release()
    return Lab(l * p.lWeight, a, b)
}
