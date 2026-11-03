package io.github.kwyrzyk.puzzlevision.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kwyrzyk.puzzlevision.vision.Params

private sealed interface Field {
    val name: String
    val hint: String
}

private class NumberField(
    override val name: String,
    override val hint: String,
    val keyboard: KeyboardType,
    val show: (Params) -> String,
    val parse: (Params, String) -> Params? // null if text is not a number
) : Field

private class SwitchField(
    override val name: String,
    override val hint: String,
    val get: (Params) -> Boolean,
    val set: (Params, Boolean) -> Params,
) : Field

private fun int(name: String, hint: String, get: (Params) -> Int, set: (Params, Int) -> Params) =
    NumberField(name, hint, KeyboardType.Number, { get(it).toString() }) { params, text ->
        text.toIntOrNull()?.let { set(params, it) }
    }

private fun real(
    name: String,
    hint: String,
    get: (Params) -> Double,
    set: (Params, Double) -> Params,
) = NumberField(name, hint, KeyboardType.Decimal, { get(it).toString() }) { params, text ->
    // Replace comma from a Polish keyboard
    text.replace(',', '.').toDoubleOrNull()?.let { set(params, it) }
}

private val SEGMENTATION: List<Field> = listOf(
    int("width", "side of the processed square, px", Params::width) { p, v -> p.copy(width = v) },
    int("blur", "Gaussian kernel, odd", Params::blur) { p, v -> p.copy(blur = v) },
    int("canny_low", "lower Canny threshold", Params::cannyLow) { p, v -> p.copy(cannyLow = v) },
    real("canny_ratio", "upper / lower Canny threshold", Params::cannyRatio) { p, v ->
        p.copy(cannyRatio = v)
    },
    int("dilation", "kernel of the edge dilation", Params::dilation) { p, v ->
        p.copy(dilation = v)
    },
    real("rect_ratio", "longest accepted rectangle, K", Params::rectRatio) { p, v ->
        p.copy(rectRatio = v)
    },
    real("m3_fraction", "limit of M3, K", Params::m3Fraction) { p, v -> p.copy(m3Fraction = v) },
    real("gamma_max", "limit of gamma, K", Params::gammaMax) { p, v -> p.copy(gammaMax = v) },
    real("sticker_min", "side / segmentation square", Params::stickerMin) { p, v ->
        p.copy(stickerMin = v)
    },
    real("sticker_max", "side / segmentation square", Params::stickerMax) { p, v ->
        p.copy(stickerMax = v)
    },
    SwitchField("hull", "shape is the convex hull of the region", Params::hull) { p, v ->
        p.copy(hull = v)
    },
    real("color_erosion", "rim dropped for the color / side", Params::colorErosion) { p, v ->
        p.copy(colorErosion = v)
    },
    real("l_weight", "weight of L in Lab", Params::lWeight) { p, v -> p.copy(lWeight = v) },
    real("segment_box", "segmentation square / screen width", Params::segmentBox) { p, v ->
        p.copy(segmentBox = v)
    },
    real("accept_box", "stickers must fit in it / screen width", Params::acceptBox) { p, v ->
        p.copy(acceptBox = v)
    },
    real("show_box", "drawn square / screen width", Params::showBox) { p, v ->
        p.copy(showBox = v)
    },
)

private val AUTO_CAPTURE: List<Field> = listOf(
    real("size_ratio", "largest / smallest sticker area", Params::sizeRatio) { p, v ->
        p.copy(sizeRatio = v)
    },
    real("align", "center off its row or column / pitch", Params::align) { p, v ->
        p.copy(align = v)
    },
    real("tilt", "degrees, also the limit of the skew", Params::tilt) { p, v -> p.copy(tilt = v) },
    real("rotation", "degrees", Params::rotation) { p, v -> p.copy(rotation = v) },
    real("delta", "limits loosened within a window", Params::delta) { p, v -> p.copy(delta = v) },
    real("motion", "center shift between frames / pitch", Params::motion) { p, v ->
        p.copy(motion = v)
    },
    real("color", "sticker color from its window median", Params::color) { p, v ->
        p.copy(color = v)
    },
    int("hold_ms", "stable time before a capture", Params::holdMs) { p, v -> p.copy(holdMs = v) },
    int("max_gap_ms", "time without a valid grid before the window drops", Params::maxGapMs) { p, v ->
        p.copy(maxGapMs = v)
    },
    real("dedup", "least center color distance from captures", Params::dedup) { p, v ->
        p.copy(dedup = v)
    },
    int("fps", "frames processed per second", Params::fps) { p, v -> p.copy(fps = v) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(params: Params, onChange: (Params) -> Unit, onBack: () -> Unit) {
    var resets by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onChange(Params())
                            resets++
                        },
                    ) {
                        Text("Reset")
                    }
                },
            )
        }
    ) { padding ->
        // The rows keep the typed text; a new key recreates them with default values
        key(resets) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Group("Segmentation", SEGMENTATION, params, onChange)
                Group("Auto-capture", AUTO_CAPTURE, params, onChange)

            }
        }
    }
}

@Composable
private fun Group(
    title: String,
    fields: List<Field>,
    params: Params,
    onChange: (Params) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    for (field in fields) {
        when (field) {
            is NumberField -> NumberRow(field, params, onChange)
            is SwitchField -> SwitchRow(field, params, onChange)
        }
    }
}

@Composable
private fun NumberRow(field: NumberField, params: Params, onChange: (Params) -> Unit) {
    var text by remember { mutableStateOf(field.show(params)) }
    var error by remember { mutableStateOf<String?>(null) }

    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            val candidate = field.parse(params, typed)
            error = if (candidate == null) "Not a number" else candidate.validate()
            if (candidate != null && error == null) onChange(candidate)
        },
        label = { Text(field.name) },
        supportingText = { Text(error ?: field.hint) },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = field.keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(field: SwitchField, params: Params, onChange: (Params) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(field.name)
            Text(field.hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = field.get(params),
            onCheckedChange = { onChange(field.set(params, it)) }
        )
    }
}