package io.github.kwyrzyk.puzzlevision.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.opencv.core.Point as CvPoint

/** End-to-end smoke tests of the OpenCV code on synthetic images; run on a device or emulator */
@RunWith(AndroidJUnit4::class)
class DetectionTest {
    private val params = Params()
    private val detector = Detector(params)

    @Test
    fun detectsTheNineStickersOfAFace() {
        val face = syntheticFace()
        val stickers = detector.detect(face.rgb)

        assertEquals(9, stickers.size)
        for (expected in face.centers) {
            val nearest = stickers.minBy { it.center.distanceTo(expected) }
            assertEquals(0.0, nearest.center.distanceTo(expected), 1.5)
        }
        // Dilated edges eat about 2 px of each side of the 40 px stickers
        assertTrue(stickers.all { it.area in 30.0 * 30.0..40.0 * 40.0 })
    }

    @Test
    fun stickerColorIsItsLab() {
        val face = syntheticFace()
        val stickers = detector.detect(face.rgb)

        for ((center, color) in face.centers.zip(FACE)) {
            val sticker = stickers.minBy { it.center.distanceTo(center) }
            assertEquals(0.0, sticker.color.distanceTo(labOf(color, params)), 1.0)
        }
    }

    @Test
    fun logoInsideAStickerIsNotItsColor() {
        val face = syntheticFace()
        val middle = face.centers[CENTER]
        Imgproc.circle(face.rgb, CvPoint(middle.x, middle.y), 8, Scalar(30.0, 30.0, 30.0), -1)
        val stickers = detector.detect(face.rgb)

        assertEquals(9, stickers.size)
        val sticker = stickers.minBy { it.center.distanceTo(middle) }
        assertEquals(0.0, sticker.color.distanceTo(labOf(BLUE, params)), 1.0)
    }

    @Test
    fun emptyFrameHasNoStickers() {
        val gray = Mat(300, 300, CvType.CV_8UC3, Scalar(128.0, 128.0, 128.0))

        val frame = detector.frame(gray)

        assertTrue(frame.stickers.isEmpty())
        assertTrue(frame.candidates.isEmpty())
    }

    @Test
    fun uprightFacePassesTheGate() {
        val face = syntheticFace()

        val pick = gate(detector.frame(face.rgb).candidates, limits(params, gate_passed = false))

        assertNotNull(pick)
        pick!!.points.zip(face.centers).forEach { (actual, expected) ->
            assertEquals(0.0, actual.distanceTo(expected), 1.5)
        }
    }

    @Test
    fun rotatedFaceKeepsItsRowsAndColumns() {
        for (degrees in listOf(-40.0, -15.0, 15.0, 40.0)) {
            val face = syntheticFace(degrees = degrees)

            val best = detector.frame(face.rgb).candidates.first()

            best.points.zip(face.centers).forEach { (actual, expected) ->
                assertEquals("at $degrees", 0.0, actual.distanceTo(expected), 1.5)
            }
            assertEquals("at $degrees", kotlin.math.abs(degrees), best.metrics.rotation, 1.0)
        }
    }

    @Test
    fun stickersOutsideTheAcceptBoxAreDropped() {
        // accept_box 0.9 leaves a margin of 15 px; the top left sticker reaches 10 px
        val face = syntheticFace(center = Point(80.0, 80.0))

        assertEquals(9, detector.detect(face.rgb).size)
        val frame = detector.frame(face.rgb)
        assertTrue(frame.stickers.size < 9)
        assertTrue(frame.candidates.isEmpty())
    }

    @Test
    fun rectangleAngleIsTheAngleOfAnyOfItsSides() {
        for (degrees in listOf(-30.0, 0.0, 15.0, 30.0, 44.0)) {
            val points = syntheticFace(degrees = degrees).centers

            val angle = Math.toRadians(rectangleAngle(points))

            // Any of the 4 directions of the sides; mod 90 as in Grid
            val mod90 = Math.toDegrees(atan2(sin(4 * angle), cos(4 * angle)) / 4)
            assertEquals("at $degrees", degrees, mod90, 0.5)
        }
    }

    @Test
    fun squareIsTheMiddleOfTheScreenUprightAndScaled() {
        // Landscape buffer, upright after 90 degrees clockwise; the screen shows rows 96 until 384
        val rgba = Mat(480, 640, CvType.CV_8UC4, Scalar(0.0, 0.0, 0.0, 255.0))
        val crop = Rect(0, 96, 640, 288)
        // The square is 288 px from x = 176; a red block at its top left corner
        Imgproc.rectangle(rgba, Rect(176, 96, 29, 29), Scalar(255.0, 0.0, 0.0, 255.0), -1)

        val square = squareOf(rgba, crop, rotation = 90, p = params)

        assertEquals(params.width, square.cols())
        assertEquals(params.width, square.rows())
        assertEquals(3, square.channels())
        // Clockwise rotation moves the top left corner to the top right
        assertEquals(255.0, square.get(10, params.width - 10)[0], 1.0)
        assertEquals(0.0, square.get(10, 10)[0], 1.0)
        assertEquals(0.0, square.get(params.width - 10, params.width - 10)[0], 1.0)
    }

    @Test
    fun heldFaceIsCapturedWithItsColors() {
        val face = syntheticFace()
        val candidates = detector.frame(face.rgb).candidates
        val autoCapture = AutoCapture(params)

        val traces = (0..3).map { autoCapture.process(it * 100L, candidates) }

        assertTrue(traces.take(3).all { it.passed && it.capture == null })
        val capture = traces[3].capture
        assertNotNull(capture)
        assertEquals(false, capture!!.duplicate)
        capture.colors.zip(FACE).forEach { (actual, expected) ->
            assertEquals(0.0, actual.distanceTo(labOf(expected, params)), 1.0)
        }
        assertNull(autoCapture.process(400L, candidates).capture)
    }

    companion object {
        const val CENTER = 4

        @JvmStatic
        @BeforeClass
        fun loadOpenCv() {
            check(OpenCVLoader.initLocal())
        }
    }
}
