package io.github.kwyrzyk.puzzlevision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kwyrzyk.puzzlevision.ui.CameraScreen
import org.opencv.android.OpenCVLoader
import io.github.kwyrzyk.puzzlevision.ui.theme.PuzzleVisionTheme
import io.github.kwyrzyk.puzzlevision.ui.MainViewModel
import io.github.kwyrzyk.puzzlevision.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(OpenCVLoader.initLocal()) { "OpenCv native library not loaded"}
        enableEdgeToEdge()
        setContent {
            PuzzleVisionTheme {
                CameraPermission {
                    App()
                }

            }
        }
    }
}

private enum class Screen { CAMERA, SETTINGS }

@Composable
private fun App(viewModel: MainViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.CAMERA) }

    when (screen) {
        Screen.CAMERA -> CameraScreen(
            showBox = viewModel.params.showBox.toFloat(),
            onSettings = { screen = Screen.SETTINGS },
        )

        Screen.SETTINGS -> {
            BackHandler { screen = Screen.CAMERA }
            SettingsScreen(
                params = viewModel.params,
                onChange = viewModel::updateParams,
                onBack = { screen = Screen.CAMERA }
            )
        }
    }
}

@Composable
fun CameraPermission(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    if (granted) {
        content()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA)}) {
                Text("Grant camera permission")
            }
        }
    }
}
