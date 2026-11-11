package io.github.kwyrzyk.puzzlevision.vision

import kotlin.math.cos
import kotlin.math.sin

/** Stickers of an ideal face, in reading order; rotated about the center of the face. */
fun face(
    rotation: Double = 0.0,
    pitch: Double = 50.0,
    center: Point = Point(150.0, 150.0),
    color: Lab = Lab(30.0, 10.0, 10.0),
    area: Double = 1600.0,
): List<Sticker> {
    val radians = Math.toRadians(rotation)
    return (-1..1).flatMap { row ->
        (-1..1).map { column ->
            val x = column * pitch
            val y = row * pitch
            Sticker(
                center = Point(
                    center.x + x * cos(radians) - y * sin(radians),
                    center.y + x * sin(radians) + y * cos(radians),
                ),
                area = area,
                color = color,
            )
        }
    }
}

/** Without OpenCV: assume the face is upright */
fun analyzeUpright(stickers: List<Sticker>): List<Candidate> = analyze(stickers) { 0.0 }
