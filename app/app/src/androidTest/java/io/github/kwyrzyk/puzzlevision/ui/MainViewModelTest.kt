package io.github.kwyrzyk.puzzlevision.ui

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kwyrzyk.puzzlevision.vision.BLUE
import io.github.kwyrzyk.puzzlevision.vision.Detector
import io.github.kwyrzyk.puzzlevision.vision.FACE
import io.github.kwyrzyk.puzzlevision.vision.Frame
import io.github.kwyrzyk.puzzlevision.vision.GREEN
import io.github.kwyrzyk.puzzlevision.vision.ORANGE
import io.github.kwyrzyk.puzzlevision.vision.RED
import io.github.kwyrzyk.puzzlevision.vision.WHITE
import io.github.kwyrzyk.puzzlevision.vision.YELLOW
import io.github.kwyrzyk.puzzlevision.vision.frame
import io.github.kwyrzyk.puzzlevision.vision.syntheticFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Scalar

/** The session as the camera drives it: frames come from a background thread */
@RunWith(AndroidJUnit4::class)
class MainViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var viewModel: MainViewModel
    private var t = 0L

    @Before
    fun createViewModel() {
        val application = instrumentation.targetContext.applicationContext as Application
        instrumentation.runOnMainSync { viewModel = MainViewModel(application) }
    }

    /** A face with the given center color, as the detection sees it */
    private fun faceWithCenter(center: Scalar): Frame {
        val colors = FACE.toMutableList().also { it[4] = center }
        val rgb = syntheticFace(colors = colors).rgb
        val frame = Detector(viewModel.params).frame(rgb)
        rgb.release()
        return frame
    }

    /** Frames 100 ms apart for 300 ms: long enough to capture with hold_ms 300 */
    private fun hold(frame: Frame) {
        repeat(4) {
            viewModel.onFrame(t, frame)
            t += 100
        }
        instrumentation.waitForIdleSync() // the UI state is updated on the main thread
    }

    @Test
    fun sixFacesEndTheSession() {
        instrumentation.runOnMainSync { viewModel.start() }

        for (center in listOf(WHITE, YELLOW, RED, ORANGE, BLUE, GREEN)) {
            hold(faceWithCenter(center))
        }

        assertEquals(6, viewModel.captures.size)
        assertFalse(viewModel.recording)
    }

    @Test
    fun sameFaceTwiceIsCountedOnce() {
        instrumentation.runOnMainSync { viewModel.start() }

        hold(faceWithCenter(RED))
        hold(faceWithCenter(RED))

        assertEquals(1, viewModel.captures.size)
        assertTrue(viewModel.recording)
    }

    @Test
    fun framesAfterStopAddNoFace() {
        val frame = faceWithCenter(RED)
        instrumentation.runOnMainSync { viewModel.start() }
        repeat(3) { viewModel.onFrame(100L * it, frame) }

        instrumentation.runOnMainSync { viewModel.stop() }
        viewModel.onFrame(300, frame)
        instrumentation.waitForIdleSync()

        assertTrue(viewModel.captures.isEmpty())
    }

    @Test
    fun startClearsTheCapturesOfTheLastSession() {
        instrumentation.runOnMainSync { viewModel.start() }
        hold(faceWithCenter(RED))

        instrumentation.runOnMainSync { viewModel.start() }

        assertTrue(viewModel.captures.isEmpty())
        assertTrue(viewModel.recording)
    }

    @Test
    fun liveGridIsShownWithoutASession() {
        viewModel.onFrame(0, faceWithCenter(RED))
        instrumentation.waitForIdleSync()

        val liveGrid = viewModel.liveGrid!!
        assertTrue(liveGrid.passed)
        assertEquals(9, liveGrid.stickers.size)
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun loadOpenCv() {
            check(OpenCVLoader.initLocal())
        }
    }
}
