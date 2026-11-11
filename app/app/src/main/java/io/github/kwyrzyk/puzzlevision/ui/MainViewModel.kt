package io.github.kwyrzyk.puzzlevision.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kwyrzyk.puzzlevision.cube.CubeState
import io.github.kwyrzyk.puzzlevision.cube.classify
import io.github.kwyrzyk.puzzlevision.data.SavedFace
import io.github.kwyrzyk.puzzlevision.data.SavedFrame
import io.github.kwyrzyk.puzzlevision.data.Session
import io.github.kwyrzyk.puzzlevision.data.SessionStore
import io.github.kwyrzyk.puzzlevision.data.SettingsStore
import io.github.kwyrzyk.puzzlevision.data.savedFrameOf
import io.github.kwyrzyk.puzzlevision.vision.AutoCapture
import io.github.kwyrzyk.puzzlevision.vision.Capture
import io.github.kwyrzyk.puzzlevision.vision.Frame
import io.github.kwyrzyk.puzzlevision.vision.Params
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val FACES = 6

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val sessions = SessionStore(application.filesDir)
    private val settings = SettingsStore(application.filesDir)
    private val writer = Dispatchers.IO.limitedParallelism(1) // saves in the order of edits

    var params by mutableStateOf(settings.load())
        private set

    var recording by mutableStateOf(false)
        private set

    /** Faces captured in this session, duplicates left out */
    val captures = mutableStateListOf<Capture>()

    var liveGrid by mutableStateOf<LiveGrid?>(null)
        private set

    /** Colors of the cube of the last complete session; null while recording */
    var result by mutableStateOf<CubeState?>(null)
        private set

    private var sessionStartedAt = 0L

    // Frames of the running session, for the timelines; main thread only
    private val frames = mutableListOf<SavedFrame>()

    /** Saved sessions, the newest first, as of the last [loadDetections] */
    var detections by mutableStateOf<List<Session>>(emptyList())
        private set

    // Written on the main thread, read on the analysis thread
    @Volatile
    private var session: AutoCapture? = null

    fun updateParams(new: Params) {
        params = new
        viewModelScope.launch(writer) { settings.save(new) }
    }

    fun start() {
        captures.clear()
        frames.clear()
        result = null
        sessionStartedAt = System.currentTimeMillis()
        session = AutoCapture(params)
        recording = true
    }

    fun stop() {
        session = null
        recording = false
    }

    /** Called on the analysis thread for every processed frame */
    fun onFrame(t: Long, frame: Frame) {
        val session = session
        val trace = session?.process(t, frame.candidates)
        val liveGrid = liveGridOf(frame, trace, params)
        // The UI state changes on the main thread
        viewModelScope.launch {
            this@MainViewModel.liveGrid = liveGrid
            // A frame of a stopped session must not add a frame or a face
            if (trace == null || session !== this@MainViewModel.session) return@launch
            frames += savedFrameOf(trace)
            val capture = trace.capture
            if (capture != null && !capture.duplicate) {
                captures += capture
                if (captures.size == FACES) {
                    stop()
                    finish()
                }
            }
        }
    }

    /** Classifies the 6 faces and saves the session; takes a few ms, fine on the main thread */
    private fun finish() {
        val faces = captures.map { SavedFace(it.first, it.last, it.colors) }
        val state = classify(faces.map { it.colors }, params)
        result = state
        val session = Session(sessionStartedAt, params, faces, state.toString(), frames.toList())
        viewModelScope.launch(writer) { sessions.save(session) }
    }

    /** Reads the saved sessions; on the writer, so after every save queued before */
    fun loadDetections() {
        viewModelScope.launch {
            detections = withContext(writer) { sessions.load() }
        }
    }
}