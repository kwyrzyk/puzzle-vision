package io.github.kwyrzyk.puzzlevision.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kwyrzyk.puzzlevision.data.SettingsStore
import io.github.kwyrzyk.puzzlevision.vision.Params
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsStore(application.filesDir)
    private val writer = Dispatchers.IO.limitedParallelism(1) // saves in the order of edits

    var params by mutableStateOf(settings.load())
        private set

    fun updateParams(new: Params) {
        params = new
        viewModelScope.launch(writer) { settings.save(new) }
    }
}