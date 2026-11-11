package io.github.kwyrzyk.puzzlevision.cube

/**
 * Cube model of `puzzle_vision/cube.py`: facelets in Kociemba's order, faces U, R, F, D, L, B,
 * nine facelets each, numbered row by row on the net:
 *
 *              U1 U2 U3
 *              U4 U5 U6
 *              U7 U8 U9
 *     L1 L2 L3 F1 F2 F3 R1 R2 R3 B1 B2 B3
 *     L4 L5 L6 F4 F5 F6 R4 R5 R6 B4 B5 B6
 *     L7 L8 L9 F7 F8 F9 R7 R8 R9 B7 B8 B9
 *              D1 D2 D3
 *              D4 D5 D6
 *              D7 D8 D9
 */

/** A face; a color is named by the face whose center has it (U = white, F = green, ...) */
enum class Face { U, R, F, D, L, B }

/** Integer 3D vector: x points to R, y to U, z to F */
data class Vector(val x: Int, val y: Int, val z: Int) {
    operator fun plus(other: Vector) = Vector(x + other.x, y + other.y, z + other.z)
    operator fun times(k: Int) = Vector(k * x, k * y, k * z)
    infix fun cross(o: Vector) = Vector(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    infix fun dot(o: Vector) = x * o.x + y * o.y + z * o.z
}

val Face.normal: Vector
    get() = when (this) {
        Face.U -> Vector(0, 1, 0)
        Face.R -> Vector(1, 0, 0)
        Face.F -> Vector(0, 0, 1)
        Face.D -> Vector(0, -1, 0)
        Face.L -> Vector(-1, 0, 0)
        Face.B -> Vector(0, 0, -1)
    }

const val FACELETS_PER_FACE = 9
const val CENTER = 4

/** Face adjacent to the top edge of each face on the net; defines the facelet numbering */
private val NET_TOP = mapOf(
    Face.U to Face.B, Face.R to Face.U, Face.F to Face.U,
    Face.D to Face.F, Face.L to Face.U, Face.B to Face.U,
)

/** Faces in the order the app asks for them: any, down, left x 3, down */
val PHOTO_ORDER = listOf(Face.U, Face.F, Face.L, Face.B, Face.R, Face.D)

/** Face at the top of the frame for each photographed face, as in research */
val PHOTO_TOP = mapOf(
    Face.U to Face.B, Face.R to Face.U, Face.F to Face.U,
    Face.D to Face.R, Face.L to Face.U, Face.B to Face.U,
)

/** One fixed place on a face; [position] is its cubie in {-1, 0, 1}^3 */
data class Facelet(val face: Face, val index: Int, val position: Vector)

/** Cubie of a cell (0..8, row by row) of [face], seen with [up] at the top */
private fun positionOf(face: Face, up: Face, cell: Int): Vector {
    val right = up.normal cross face.normal
    val dx = cell % 3 - 1
    val dy = 1 - cell / 3
    return face.normal + right * dx + up.normal * dy
}

val FACELETS: List<Facelet> = Face.entries.flatMap { face ->
    (0 until FACELETS_PER_FACE).map { index ->
        Facelet(face, index, positionOf(face, NET_TOP.getValue(face), index))
    }
}

/** Index of a facelet in [FACELETS] */
fun idOf(face: Face, index: Int): Int = face.ordinal * FACELETS_PER_FACE + index

/** Id of Facelets of given cubie, facing [face] of cube */
private fun idAt(position: Vector, face: Face): Int =
    FACELETS.indexOfFirst { it.position == position && it.face == face }

/** Facelets indices (0..8) of [face] in reading order, seen with [up] at the top */
fun faceView(face: Face, up: Face): List<Int> =
    (0 until FACELETS_PER_FACE).map { idAt(positionOf(face, up, it), face) % FACELETS_PER_FACE }

/** Pieces in Kociemba's order; letter in clockwise order, the U/D or (F/B) face first */
val CORNERS = listOf("URF", "UFL", "ULB", "UBR", "DFR", "DLF", "DBL", "DRB")
val EDGES = listOf("UR", "UF", "UL", "UB", "DR", "DF", "DL", "DB", "FR", "FL", "BL", "BR")

fun faceOf(letter: Char): Face = Face.valueOf(letter.toString())

/** Facelet ids of the place of a piece, in the order of its name */
fun placeOf(name: String): List<Int> {
    val faces = name.map(::faceOf)
    val position = faces.map { it.normal }.reduce(Vector::plus)
    return faces.map { idAt(position, it) }
}

/** Colors of the 54 facelets in the order of [FACELETS] */
data class CubeState(val colors: List<Face>) {
    /** Kociemba's facelet string, e.g. UUUUUUUUURRR... for the solved cube */
    override fun toString(): String = colors.joinToString("") { it.name }

    companion object {
        fun of(facelets: String) = CubeState(facelets.map(::faceOf))
    }
}

