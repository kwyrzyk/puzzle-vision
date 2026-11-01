package io.github.kwyrzyk.puzzlevision.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation

private const val FACES = 6
private const val SHOW_BOX = 0.8f // move it to settings params latter

private enum class Hint { ANY, DOWN, LEFT }

// Which face to show next hint
private val HINTS = listOf(Hint.ANY, Hint.DOWN, Hint.LEFT, Hint.LEFT, Hint.LEFT, Hint.DOWN)

@Composable
fun CameraScreen() {
    var recording by remember { mutableStateOf(false) }
    var captured by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(Modifier.fillMaxSize())
        ShowBox(SHOW_BOX, Color.White, Modifier.fillMaxSize())

        Box(Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)) {
            FilledTonalIconButton(
                onClick = { /* detections button onclick */ },
                enabled = !recording,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Icon(Icons.Filled.History, contentDescription = "Detections")
            }
            FilledTonalIconButton(
                onClick = { /* settings onclick */ },
                enabled = !recording,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
            Label("$captured/$FACES", Modifier.align(Alignment.TopCenter))

            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (recording) {
                    NextFace(HINTS[captured])
                    TextButton(
                        onClick = {
                            captured++
                            if (captured == FACES) recording = false
                        }
                    ) {
                        Text("+1 (temporary)", color = Color.White)
                    }
                }
                Button(
                    onClick = {
                        if (!recording) captured = 0
                        recording = !recording
                    },
                ) {
                    Text(if (recording) "STOP" else "START")
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        try {
            awaitCancellation()
        } finally {
            provider.unbindAll()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun ShowBox(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = fraction * size.width
        drawRoundRect(
            color = color,
            topLeft = Offset((size.width - side) / 2, (size.height - side) / 2),
            size = Size(side, side),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun NextFace(hint: Hint) {
    when (hint) {
        Hint.ANY -> Label("Show any face")
        Hint.DOWN -> Arrow(Icons.Filled.ArrowDownward, "Show bottom face")
        Hint.LEFT -> Arrow(Icons.AutoMirrored.Filled.ArrowBack, "Show left face")
    }
}

@Composable
private fun Arrow(icon: ImageVector, description: String) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = Color.White,
        modifier = Modifier
            .size(72.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(8.dp)
    )
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 20.sp,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
