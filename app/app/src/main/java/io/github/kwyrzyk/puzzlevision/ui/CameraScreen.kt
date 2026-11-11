package io.github.kwyrzyk.puzzlevision.ui

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.kwyrzyk.puzzlevision.camera.FrameAnalyzer
import io.github.kwyrzyk.puzzlevision.vision.Point
import java.util.concurrent.Executors

private enum class Hint { ANY, DOWN, LEFT }

// Which face to show next hint
private val HINTS = listOf(Hint.ANY, Hint.DOWN, Hint.LEFT, Hint.LEFT, Hint.LEFT, Hint.DOWN)

@Composable
fun CameraScreen(viewModel: MainViewModel, onSettings: () -> Unit) {
    val params = viewModel.params
    val recording = viewModel.recording
    val captured = viewModel.captures.size
    val analyzer = remember(viewModel) { FrameAnalyzer({ viewModel.params }, viewModel::onFrame) }

    // After each capture: 0 to 1 and back 3 times; the box blinks green, the hint pulses
    val flash = remember { Animatable(0f) }
    LaunchedEffect(captured) {
        if (captured == 0) return@LaunchedEffect
        repeat(3) {
            flash.animateTo(1f, tween(150))
            flash.animateTo(0f, tween(150))
        }
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(analyzer, Modifier.fillMaxSize())
        ShowBox(
            params.showBox.toFloat(),
            lerp(Color.White, Color.Green, flash.value),
            Modifier.fillMaxSize(),
        )
        GridOverlay(viewModel.liveGrid, params.segmentBox.toFloat(), Modifier.fillMaxSize())

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
                onClick = onSettings,
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
                    Box(Modifier.graphicsLayer {
                        scaleX = 1 + 0.5f * flash.value
                        scaleY = 1 + 0.5f * flash.value
                    }) {
                        NextFace(HINTS[captured])
                    }
                }
                Button(onClick = { if (recording) viewModel.stop() else viewModel.start() }) {
                    Text(if (recording) "STOP" else "START")
                }
            }
        }
    }
}
/** Preview and analysis share the crop of the screen, so the boxes match the analyzed square */
@Composable
private fun CameraPreview(analyzer: ImageAnalysis.Analyzer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS) // preview is always on
            setImageAnalysisOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            setImageAnalysisResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(1280, 960),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
        }
    }

    DisposableEffect(lifecycleOwner, analyzer) {
        val executor = Executors.newSingleThreadExecutor()
        controller.setImageAnalysisAnalyzer(executor, analyzer)
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            executor.shutdown()
        }
    }
    AndroidView(
        factory = {
            PreviewView(it).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = modifier)
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

/** Sticker centers as dots and the grid lines; green when the grid passed the gate */
@Composable
private fun GridOverlay(liveGrid: LiveGrid?, segmentBox: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (liveGrid == null) return@Canvas
        // The segmentation square is segment_box x the screen width, in the middle
        val side = segmentBox * size.width
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        val scale = side / liveGrid.width
        fun at(p: Point) = Offset(left + p.x.toFloat() * scale, top + p.y.toFloat() * scale)

        for (center in liveGrid.stickers) {
            drawCircle(Color.White, radius = 4.dp.toPx(), center = at(center))
        }
        val grid = liveGrid.grid ?: return@Canvas
        val color = if (liveGrid.passed) Color.Green else Color.Red
        val width = 3.dp.toPx()
        for (i in 0..2) {
            drawLine(color, at(grid[3 * i]), at(grid[3 * i + 2]), width) // row
            drawLine(color, at(grid[i]), at(grid[i + 6]), width) // column
        }

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
