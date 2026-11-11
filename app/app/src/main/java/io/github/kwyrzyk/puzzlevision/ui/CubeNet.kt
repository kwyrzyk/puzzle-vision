package io.github.kwyrzyk.puzzlevision.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kwyrzyk.puzzlevision.cube.FACELETS
import io.github.kwyrzyk.puzzlevision.cube.Face
import io.github.kwyrzyk.puzzlevision.cube.CubeState

/** Colors of the standard scheme: white up, green front */
private val FACE_COLORS = mapOf(
    Face.U to Color.White,
    Face.R to Color(0xFFD32F2F),
    Face.F to Color(0xFF2E7D32),
    Face.D to Color(0xFFFDD835),
    Face.L to Color(0xFFF57C00),
    Face.B to Color(0xFF1565C0),
)

/** Column and row of the top left facelet of each face on the net, 12 x 9 facelets */
private val NET_ORIGIN = mapOf(
    Face.U to (3 to 0),
    Face.L to (0 to 3),
    Face.F to (3 to 3),
    Face.R to (6 to 3),
    Face.B to (9 to 3),
    Face.D to (3 to 6),
)

/** The state drawn as the net of `Cube.kt` */
@Composable
fun CubeNet(state: CubeState, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(12f / 9f)) {
        val cell = size.width / 12
        val gap = 1.dp.toPx()
        for ((id, facelet) in FACELETS.withIndex()) {
            val (column, row) = NET_ORIGIN.getValue(facelet.face)
            drawRect(
                color = FACE_COLORS.getValue(state.colors[id]),
                topLeft = Offset(
                    (column + facelet.index % 3) * cell + gap,
                    (row + facelet.index / 3) * cell + gap,
                ),
                size = Size(cell - 2 * gap, cell - 2 * gap),
            )
        }
    }
}