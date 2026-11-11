package io.github.kwyrzyk.puzzlevision.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import io.github.kwyrzyk.puzzlevision.vision.Detector
import io.github.kwyrzyk.puzzlevision.vision.Frame
import io.github.kwyrzyk.puzzlevision.vision.Params
import io.github.kwyrzyk.puzzlevision.vision.frame
import io.github.kwyrzyk.puzzlevision.vision.squareOf
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect

/**
 * Detection on camera frames, `fps` of them per second; runs on one background thread.
 *
 * [params] is read on every frame, so new settings apply at once. [onFrame] gets the time in ms.
 */
class FrameAnalyzer(
    private val params: () -> Params,
    private val onFrame: (t: Long, frame: Frame) -> Unit,
) : ImageAnalysis.Analyzer {
    private var detector: Detector? = null
    private var slot = -1L

    override fun analyze(image: ImageProxy) {
        image.use {
            val p = params()
            val t = image.imageInfo.timestamp / 1_000_000
            // First frame of each 1 / fps slot, the others are skipped
            val current = t * p.fps / 1000
            if (current <= slot) return
            slot = current

            val detector = detector?.takeIf { it.p == p } ?: Detector(p).also { detector = it }
            val rgba = rgbaOf(image)
            val crop = image.cropRect
            val square = squareOf(
                rgba,
                Rect(crop.left, crop.top, crop.width(), crop.height()),
                image.imageInfo.rotationDegrees,
                p,
            )
            rgba.release()
            val frame = detector.frame(square)
            square.release()
            onFrame(t, frame)
        }
    }
}

/** The RGBA pixels of the image without a copy; valid only until the image is closed */
private fun rgbaOf(image: ImageProxy): Mat {
    val plane = image.planes[0]
    return Mat(image.height, image.width, CvType.CV_8UC4, plane.buffer, plane.rowStride.toLong())
}