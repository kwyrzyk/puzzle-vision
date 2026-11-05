package io.github.kwyrzyk.puzzlevision.vision

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* Params kept in notebook 3.3. naming convention for compatibility */
@Serializable
data class Params(
    // Segmentation
    val width: Int = 300,
    val blur: Int = 3,
    @SerialName("canny_low") val cannyLow: Int = 40,
    @SerialName("canny_ratio") val cannyRatio: Double = 2.0,
    val dilation: Int = 5,
    @SerialName("rect_ratio") val rectRatio: Double = 1.38,
    @SerialName("m3_fraction") val m3Fraction: Double = 0.1,
    @SerialName("gamma_max") val gammaMax: Double = 0.2,
    @SerialName("sticker_min") val stickerMin: Double = 0.08,
    @SerialName("sticker_max") val stickerMax: Double = 0.3,
    val hull: Boolean = false,
    @SerialName("color_erosion") val colorErosion: Double = 0.1,
    @SerialName("l_weight") val lWeight: Double = 0.3,
    @SerialName("segment_box") val segmentBox: Double = 1.0,
    @SerialName("accept_box") val acceptBox: Double = 0.9,
    @SerialName("show_box") val showBox: Double = 0.8,
    // Auto-capture
    @SerialName("size_ratio") val sizeRatio: Double = 1.5,
    val align: Double = 0.1,
    val tilt: Double = 25.0,
    val rotation: Double = 10.0,
    val delta: Double = 0.0,
    val motion: Double = 0.1,
    @SerialName("color") val colorDrift: Double = 4.0,
    @SerialName("hold_ms") val holdMs: Int = 300,
    @SerialName("max_gap_ms") val maxGapMs: Int = 200,
    val dedup: Double = 6.0,
    val fps: Int = 10,
) {
    /** The first problem found, or null when all values are valid */
    fun validate(): String? = listOfNotNull(
        outside("width", width, 100..1080),
        outside("blur", blur, 1..15),
        if (blur % 2 == 0) "blur must be odd" else null,
        outside("canny_low", cannyLow, 1..255),
        outside("canny_ratio", cannyRatio, 1.0..5.0),
        outside("dilation", dilation, 1..15),
        outside("rect_ratio", rectRatio, 1.0..5.0),
        outside("m3_fraction", m3Fraction, 0.0..1.0),
        outside("gamma_max", gammaMax, 0.0..1.0),
        outside("sticker_min", stickerMin, 0.0..1.0),
        outside("sticker_max", stickerMax, 0.0..1.0),
        if (stickerMin >= stickerMax) "sticker_min must be below sticker_max" else null,
        outside("color_erosion", colorErosion, 0.0..0.5),
        outside("l_weight", lWeight, 0.0..1.0),
        outside("segment_box", segmentBox, 0.1..1.0),
        outside("accept_box", acceptBox, 0.1..1.0),
        outside("show_box", showBox, 0.1..1.0),
        if (acceptBox >= segmentBox) "accept_box must be below segment_box" else null,
        if (showBox > acceptBox) "show_box must not exceed accept_box" else null,
        outside("size_ratio", sizeRatio, 1.0..10.0),
        outside("align", align, 0.0..1.0),
        outside("tilt", tilt, 0.0..90.0),
        outside("rotation", rotation, 0.0..45.0),
        outside("delta", delta, 0.0..1.0),
        outside("motion", motion, 0.0..1.0),
        outside("color", colorDrift, 0.0..100.0),
        outside("hold_ms", holdMs, 0..5000),
        outside("max_gap_ms", maxGapMs, 0..5000),
        outside("dedup", dedup, 0.0..100.0),
        outside("fps", fps, 1..30),
    ).firstOrNull()
}

private fun <T: Comparable<T>> outside(name: String, value: T, range: ClosedRange<T>): String? =
    if (value in range) null else "$name must be in ${range.start}..${range.endInclusive}"