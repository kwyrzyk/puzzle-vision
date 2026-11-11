package io.github.kwyrzyk.puzzlevision.camera

import android.annotation.SuppressLint
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kwyrzyk.puzzlevision.vision.FACE
import io.github.kwyrzyk.puzzlevision.vision.Frame
import io.github.kwyrzyk.puzzlevision.vision.Params
import io.github.kwyrzyk.puzzlevision.vision.labOf
import io.github.kwyrzyk.puzzlevision.vision.syntheticFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

/** The analyzer on fake camera images: RGBA rows padded as CameraX may give them */
@RunWith(AndroidJUnit4::class)
class FrameAnalyzerTest {
    private val params = Params()

    @Test
    fun cameraImageGivesTheStickersAndTheGrid() {
        val frames = mutableListOf<Frame>()
        val analyzer = FrameAnalyzer({ params }) { _, frame -> frames += frame }

        analyzer.analyze(fakeImage(timestampMs = 1000, rotation = 0))

        assertEquals(1, frames.size)
        assertEquals(9, frames[0].stickers.size)
        assertTrue(frames[0].candidates.isNotEmpty())
    }

    @Test
    fun framesWithinOneSlotOfFpsAreSkipped() {
        val times = mutableListOf<Long>()
        val analyzer = FrameAnalyzer({ params }) { t, _ -> times += t }

        // fps 10: slots of 100 ms
        for (ms in listOf(1000L, 1030L, 1060L, 1099L, 1100L, 1150L, 1230L)) {
            analyzer.analyze(fakeImage(timestampMs = ms, rotation = 0))
        }

        assertEquals(listOf(1000L, 1100L, 1230L), times)
    }

    @Test
    fun everyImageIsClosed() {
        val analyzer = FrameAnalyzer({ params }) { _, _ -> }
        val images = listOf(1000L, 1010L).map { fakeImage(timestampMs = it, rotation = 0) }

        images.forEach { analyzer.analyze(it) }

        assertTrue(images.all { it.closed })
    }

    @Test
    fun rotatedImageIsAnalyzedUpright() {
        val frames = mutableListOf<Frame>()
        val analyzer = FrameAnalyzer({ params }) { _, frame -> frames += frame }

        // The buffer holds the face turned 90 degrees counterclockwise; rotation 90 undoes it
        analyzer.analyze(fakeImage(timestampMs = 1000, rotation = 90))

        // Rotating the wrong way would turn the face upside down: the colors in reverse order
        val expected = FACE.map { labOf(it, params) }
        val colors = frames.single().candidates.first().colors
        colors.zip(expected).forEach { (actual, color) ->
            assertEquals(0.0, actual.distanceTo(color), 1.0)
        }
    }

    /** A 300 x 300 synthetic face as RGBA with 64 bytes of padding per row */
    private fun fakeImage(timestampMs: Long, rotation: Int): FakeImage {
        val face = syntheticFace().rgb
        if (rotation == 90) Core.rotate(face, face, Core.ROTATE_90_COUNTERCLOCKWISE)
        val rgba = Mat()
        Imgproc.cvtColor(face, rgba, Imgproc.COLOR_RGB2RGBA)
        val width = rgba.cols()
        val height = rgba.rows()
        val rowStride = width * 4 + 64
        val buffer = ByteBuffer.allocateDirect(rowStride * height)
        val row = ByteArray(width * 4)
        for (y in 0 until height) {
            rgba.get(y, 0, row)
            buffer.position(y * rowStride)
            buffer.put(row)
        }
        buffer.rewind()
        face.release()
        rgba.release()
        return FakeImage(width, height, buffer, rowStride, timestampMs * 1_000_000, rotation)
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun loadOpenCv() {
            check(OpenCVLoader.initLocal())
        }
    }
}

@SuppressLint("RestrictedApi")
private class FakeImage(
    private val width: Int,
    private val height: Int,
    buffer: ByteBuffer,
    rowStride: Int,
    timestampNs: Long,
    rotation: Int,
) : ImageProxy {
    var closed = false
        private set

    private val plane = object : ImageProxy.PlaneProxy {
        override fun getRowStride() = rowStride
        override fun getPixelStride() = 4
        override fun getBuffer() = buffer
    }

    private val info = object : ImageInfo {
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
        override fun getTimestamp() = timestampNs
        override fun getRotationDegrees() = rotation
        override fun populateExifData(exifBuilder: ExifData.Builder) = Unit
    }

    private var crop = Rect(0, 0, width, height)

    override fun close() {
        closed = true
    }

    override fun getCropRect() = crop
    override fun setCropRect(rect: Rect?) {
        crop = rect ?: Rect(0, 0, width, height)
    }

    override fun getFormat() = android.graphics.PixelFormat.RGBA_8888
    override fun getHeight() = height
    override fun getWidth() = width
    override fun getPlanes() = arrayOf(plane)
    override fun getImageInfo() = info
    override fun getImage(): Image? = null
}
