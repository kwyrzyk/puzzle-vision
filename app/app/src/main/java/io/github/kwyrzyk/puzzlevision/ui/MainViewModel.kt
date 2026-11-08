package io.github.kwyrzyk.puzzlevision.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kwyrzyk.puzzlevision.data.SettingsStore
import io.github.kwyrzyk.puzzlevision.vision.AutoCapture
import io.github.kwyrzyk.puzzlevision.vision.Capture
import io.github.kwyrzyk.puzzlevision.vision.Frame
import io.github.kwyrzyk.puzzlevision.vision.Params
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val FACES = 6

class MainViewModel(application: Application) : AndroidViewModel(application) {
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

    // Written on the main thread, read on the analysis thread
    @Volatile
    private var session: AutoCapture? = null

    fun updateParams(new: Params) {
        params = new
        viewModelScope.launch(writer) { settings.save(new) }
    }

    fun start() {
        captures.clear()
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
            val capture = trace?.capture
            // A frame of a stopped session must not add a face
            if (capture != null && !capture.duplicate && session === this@MainViewModel.session) {
                captures += capture
                if (captures.size == FACES) stop()
            }
        }
    }
}