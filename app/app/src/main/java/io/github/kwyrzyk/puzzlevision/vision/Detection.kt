package io.github.kwyrzyk.puzzlevision.vision

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import org.opencv.core.Point as CvPoint

/** S2 gray + K of notebook 3.3; one instance per [Params], used by one thread */
class Detector(val p: Params) {
    private val limits = Shape(
        m1 = 0.17577855863304376,
        m2 = 0.0031203238973326946,
        m3 = 0.00026440822915418515,
        m7 = 0.006334503369093386,
        gamma = p.gammaMax,
    )

    /**Stickers of an RGB frame; coordinate in its pixels */
    fun detect(rgb: Mat): List<Sticker> {
        val candidates = regions(rgb)
        val accepted = withoutNested(candidates.filter {
            shapeOf(it.shape).within(limits)
        })
        val lab = labOf(rgb)
        val stickers = accepted.map { Sticker(it.center, it.area, colorOf(lab, it)) }
        lab.release()
        candidates.forEach { it.release() }
        return stickers
    }

    private fun regions(rgb: Mat): List<Region> {
        val width = rgb.cols()
        val low = p.stickerMin * width
        val high = p.stickerMax * width

        val gray = Mat()
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(p.blur.toDouble(), p.blur.toDouble()), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, p.cannyLow.toDouble(), p.cannyLow * p.cannyRatio)
        val kernel = Mat.ones(p.dilation, p.dilation, CvType.CV_8U)
        Imgproc.dilate(edges, edges, kernel)
        Core.bitwise_not(edges, edges)

        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(edges, labels, stats, centroids, 8, CvType.CV_32S)
        val s = IntArray(count * 5) // x, y, w, h, area
        stats.get(0, 0, s)

        val out = mutableListOf<Region>()
        for (label in 1 until count) {
            val box = Rect(s[5 * label], s[5 * label + 1], s[5 * label +2], s[5 * label + 3])
            // A rotated square fills half it box - fast reject
            if (sqrt(box.area()) !in low..high * sqrt(2.0)) continue

            val inBox = labels.submat(box)
            val mask = Mat()
            Core.compare(inBox, Scalar(label.toDouble()), mask, Core.CMP_EQ)
            inBox.release()
            var shape = contourOf(mask, offset = CvPoint(box.x.toDouble(), box.y.toDouble()))
            if (p.hull) shape = hullOf(shape)
            val area = Geometry.contourArea(shape)
            if (sqrt(area) in low..high) {
                out += Region(shape, area, mask, box, centerOf(shape))
            } else {
                shape.release()
                mask.release()
            }
        }
        listOf(gray, edges, kernel, labels, stats, centroids).forEach { it.release() }
        return out
    }

    /** Largest first; a region whose center lies inside a kept shape is dropped */
    private fun withoutNested(regions: List<Region>): List<Region> {
        val kept = mutableListOf<Region>()
        val curves = mutableListOf<MatOfPoint2f>()
        for (region in regions.sortedByDescending { it.area }) {
            val center = CvPoint(region.center.x, region.center.y)
            if (curves.all { Geometry.pointPolygonTest(it, center, false) < 0 }) {
                kept += region
                curves += curveOf(region.shape)
            }
        }
        curves.forEach { it.release() }
        return kept
    }

    /** Median Lab of the region eroded by `color_erosion` x side; holes are not part of it */
    private fun colorOf(lab: Mat, region: Region): Lab {
        val size = max(1, Math.rint(p.colorErosion * sqrt(region.area)).toInt())
        val kernel = Mat.ones(size, size, CvType.CV_8U)
        val eroded = Mat()
        Imgproc.erode(region.mask, eroded, kernel)
        val mask = if (Core.countNonZero(eroded) > 0) eroded else region.mask

        val w = region.box.width
        val h = region.box.height
        val inside = ByteArray(w * h)
        mask.get(0, 0, inside)
        val inBox = lab.submat(region.box)
        val pixels = FloatArray(w * h * 3)
        inBox.get(0, 0, pixels)
        listOf(kernel, eroded, inBox).forEach { it. release() }

        val count = inside.count { it != 0.toByte() }
        val channels = List(3) { FloatArray(count) }
        var n = 0
        for (i in inside.indices) {
            if (inside[i] == 0.toByte()) continue
            for  (c in 0..2) channels[c][n] = pixels[3 * i + c]
            n++
        }
        val (l, a, b) = channels.map { median(it) }
        return Lab(l, a, b)
    }

    /** Lab with L weighted by `l_weight`, as floats */
    private fun labOf(rgb: Mat): Mat {
        val lab = Mat()
        rgb.convertTo(lab, CvType.CV_32FC3, 1.0 / 255)
        Imgproc.cvtColor(lab, lab, Imgproc.COLOR_RGB2Lab)
        Core.multiply(lab, Scalar(p.lWeight, 1.0, 1.0), lab)
        return lab
    }
}

/** A region of a sticker size; [mask] covers [box], 255, on the pixels of the region */
private class Region(
    val shape: MatOfPoint,
    val area: Double,
    val mask: Mat,
    val box: Rect,
    val center: Point
) {
    fun release() {
        shape.release()
        mask.release()
    }
}


private class Shape(
    val m1: Double,
    val m2: Double,
    val m3: Double,
    val m7: Double,
    val gamma: Double,
) {
    /** M7 has a lower limit, the other an upper one */
    fun within(limit: Shape): Boolean =
        m1 <= limit.m1 && m2 <= limit.m2 && m3 <= limit.m3 && m7 >= limit.m7
                && gamma >= 0 && gamma <= limit.gamma
}

private fun contourOf(mask: Mat, offset: CvPoint = CvPoint()): MatOfPoint {
    val contours = mutableListOf<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(
        mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE, offset,
    )
    hierarchy.release()
    val largest = contours.maxBy { Geometry.contourArea(it) }
    contours.filter { it !== largest}.forEach { it.release() }
    return largest
}

private fun hullOf(contour: MatOfPoint): MatOfPoint {
    val indices = MatOfInt()
    Geometry.convexHull(contour, indices)
    val points = contour.toArray()
    val hull = MatOfPoint(*indices.toArray().map {points[it]}.toTypedArray())
    indices.release()
    contour.release()
    return hull
}

private fun curveOf(contour: MatOfPoint): MatOfPoint2f {
    val curve = MatOfPoint2f()
    contour.convertTo(curve, CvType.CV_32FC2)
    return curve
}

/** Center of the bounding box */
private fun centerOf(contour: MatOfPoint): Point {
    val box = Geometry.boundingRect(contour)
    return Point(box.x + box.width / 2.0, box.y + box.height / 2.0)
}

private fun shapeOf(contour: MatOfPoint): Shape {
    val m = Geometry.moments(contour)
    val m00 = if (m.m00 != 0.0) m.m00 else 1e-9
    val curve = curveOf(contour)
    val perimeter = Geometry.arcLength(curve, true)
    curve.release()
    return Shape(
        m1 = (m.mu20 + m.mu02) / m00.pow(2),
        m2 = ((m.mu20 - m.mu02).pow(2) + 4 * m.mu11.pow(2)) / m00.pow(4),
        m3 = ((m.mu30 - 3 * m.mu12).pow(2) + (3 * m.mu21 - m.mu03).pow(2)) / m00.pow(5),
        m7 = (m.mu20 * m.mu02 - m.mu11.pow(2)) / m00.pow(4),
        gamma = perimeter / (2 * sqrt(PI * m00)) - 1,
    )
}

private fun median(values: FloatArray): Double {
    values.sort()
    val middle = values.size / 2
    return if (values.size % 2 == 1) values[middle].toDouble()
    else (values[middle - 1].toDouble() + values[middle]) / 2
}