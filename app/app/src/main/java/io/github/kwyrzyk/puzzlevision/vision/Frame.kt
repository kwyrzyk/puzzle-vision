package io.github.kwyrzyk.puzzlevision.vision

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.opencv.core.Point as CvPoint

/** Stickers within the accept box and the candidates among them; coordinates in the square */
class Frame(val stickers: List<Sticker>, val candidates: List<Candidate>)

/**
 * The segmentation square of a camera frame as RGB, upright, `width` px wide.
 *
 * [crop] is the part of [rgba] visible on the screen; [rotation] in degrees clockwise makes it
 * upright. The square is `segment_box` x the screen width, in the middle of the screen.
 */
fun squareOf(rgba: Mat, crop: Rect, rotation: Int, p: Params): Mat {
    val screenWidth = if (rotation % 180 == 0) crop.width else crop.height
    val side = min((p.segmentBox * screenWidth).roundToInt(), min(crop.width, crop.height))
    val x = crop.x + (crop.width - side) / 2
    val y = crop.y + (crop.height - side) / 2

    val inSquare = rgba.submat(Rect(x, y, side, side))
    val square = Mat()
    val size = Size(p.width.toDouble(), p.width.toDouble())
    Imgproc.resize(inSquare, square, size, 0.0, 0.0, Imgproc.INTER_AREA)
    inSquare.release()
    Imgproc.cvtColor(square, square, Imgproc.COLOR_RGBA2RGB)
    when (rotation) {
        90 -> Core.rotate(square, square, Core.ROTATE_90_CLOCKWISE)
        180 -> Core.rotate(square, square, Core.ROTATE_180)
        270 -> Core.rotate(square, square, Core.ROTATE_90_COUNTERCLOCKWISE)
    }
    return square
}

/** Stickers of the square that fit in the accept box, and the candidates among them */
fun Detector.frame(square: Mat): Frame {
    val width = square.cols().toDouble()
    val margin = width * (1 - p.acceptBox/ p.segmentBox) / 2
    val box = margin..width - margin
    val stickers = detect(square).filter {
        val half = sqrt(it.area) / 2
        it.center.x - half in box && it.center.x + half in box &&
                it.center.y - half in box && it.center.y + half in box
    }
    return Frame(stickers, analyze(stickers, ::rectangleAngle))
}

/** Angle in degrees of the smallest rectangle around the points */
fun rectangleAngle(points: List<Point>): Double {
    val mat = MatOfPoint2f(*points.map { CvPoint(it.x, it.y) }.toTypedArray())
    val angle = Geometry.minAreaRect(mat).angle
    mat.release()
    return angle
}