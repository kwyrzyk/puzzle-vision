package io.github.kwyrzyk.puzzlevision.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.kwyrzyk.puzzlevision.cube.CubeState
import io.github.kwyrzyk.puzzlevision.data.Session
import io.github.kwyrzyk.puzzlevision.vision.Lab
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Saved sessions, the newest first; a tap opens one */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionsScreen(sessions: List<Session>, onBack: () -> Unit) {
    // startedAt of the open session; saveable, so it survives a screen rotation
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }
    val session = sessions.firstOrNull { it.startedAt == opened }
    // Declared after the one in App, so it goes first while a session is open
    BackHandler(enabled = session != null) { opened = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (session == null) "Detections" else dateOf(session.startedAt)) },
                navigationIcon = {
                    IconButton(onClick = { if (session == null) onBack() else opened = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        if (session == null) {
            SessionList(sessions, onOpen = { opened = it.startedAt }, modifier)
        } else {
            SessionDetail(session, modifier)
        }
    }
}

@Composable
private fun SessionList(sessions: List<Session>, onOpen: (Session) -> Unit, modifier: Modifier) {
    if (sessions.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No detections yet")
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(sessions, key = { it.startedAt }) { session ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(session) },
            ) {
                CubeNet(CubeState.of(session.state), Modifier.width(120.dp).netBackground())
                Text(dateOf(session.startedAt))
            }
        }
    }
}

@Composable
private fun SessionDetail(session: Session, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Classified", style = MaterialTheme.typography.titleMedium)
        CubeNet(CubeState.of(session.state), Modifier.fillMaxWidth().netBackground())
        Text(session.state, fontFamily = FontFamily.Monospace)

        Text("Captured colors", style = MaterialTheme.typography.titleMedium)
        // In the order of capture, each seen in the frame
        for ((row, faces) in session.faces.chunked(3).withIndex()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for ((k, face) in faces.withIndex()) {
                    Column(Modifier.weight(1f)) {
                        Text("${3 * row + k + 1}")
                        CapturedFace(face.colors, session.params.lWeight)
                    }
                }
            }
        }

        Text("Timelines", style = MaterialTheme.typography.titleMedium)
        Timelines(session.frames, session.params)
    }
}

/** Dark background, so white facelets stand out in light theme */
private fun Modifier.netBackground() =
    background(Color.Black, RoundedCornerShape(8.dp)).padding(4.dp)

/** The 9 colors of a capture in reading order */
@Composable
private fun CapturedFace(colors: List<Lab>, lWeight: Double, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().aspectRatio(1f)) {
        val cell = size.width / 3
        val gap = 1.dp.toPx()
        colors.forEachIndexed { i, lab ->
            drawRect(
                color = displayColorOf(lab, lWeight),
                topLeft = Offset((i % 3) * cell + gap, (i / 3) * cell + gap),
                size = Size(cell - 2 * gap, cell - 2 * gap),
            )
        }
    }
}

/** sRGB of a captured color; its L was multiplied by `l_weight` */
fun displayColorOf(lab: Lab, lWeight: Double): Color {
    val l = if (lWeight > 0) lab.l / lWeight else 50.0 // l_weight 0 loses the lightness
    return Color(
        l.toFloat().coerceIn(0f, 100f),
        lab.a.toFloat().coerceIn(-128f, 128f),
        lab.b.toFloat().coerceIn(-128f, 128f),
        colorSpace = ColorSpaces.CieLab,
    ).convert(ColorSpaces.Srgb)
}

/** Date and time of [ms] since 1970, in [zone] */
fun dateOf(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(zone)
        .format(Instant.ofEpochMilli(ms))