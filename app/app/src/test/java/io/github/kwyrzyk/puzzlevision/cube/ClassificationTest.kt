package io.github.kwyrzyk.puzzlevision.cube

import io.github.kwyrzyk.puzzlevision.vision.Lab
import io.github.kwyrzyk.puzzlevision.vision.Params
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificationTest {
    private val params = Params()

    /** Palette of notebook 3.1, Lab without the weight of L */
    private val palette = mapOf(
        Face.U to Lab(100.0, 0.0, 0.0),
        Face.R to Lab(47.3, 68.6, 45.3),
        Face.F to Lab(57.2, -60.8, 58.7),
        Face.D to Lab(83.3, -10.1, 83.5),
        Face.L to Lab(67.5, 41.8, 74.3),
        Face.B to Lab(32.6, 41.2, -75.7),
    )

    private fun colorOf(face: Face): Lab =
        palette.getValue(face).let { it.copy(l = it.l * params.lWeight) }

    /** The 6 captures of a state as the app takes them: photo order, each face turned */
    private fun capturesOf(state: CubeState, color: (Face) -> Lab = ::colorOf): List<List<Lab>> =
        PHOTO_ORDER.map { face ->
            faceView(face, PHOTO_TOP.getValue(face)).map { index ->
                color(state.colors[idOf(face, index)])
            }
        }

    @Test
    fun solvedCubeIsClassified() {
        val solved = CubeState.of("UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB")

        assertEquals(solved, classify(capturesOf(solved), params))
    }

    @Test
    fun scrambledCubeIsClassified() {
        // R U R' U' F2 D L' B2 U2 R D' F, from CubeState.from_scramble
        val scrambled = CubeState.of("DDLFUFFBBRLFDRBRRBUDULFRBUFLFDDDURBLLRRRLUDLDUFFUBBULB")

        assertEquals(scrambled, classify(capturesOf(scrambled), params))
    }

    @Test
    fun firstFaceMayBeAny() {
        // The scrambled cube turned about the R axis: white is not up, green not in front;
        // the result is turned back
        val turned = CubeState.of("BLUBBUFFUFBBLRRRDRDDLFUFFBBUDULFRBUFDRLLLRDURLBRUDDDFL")
        val scrambled = CubeState.of("DDLFUFFBBRLFDRBRRBUDULFRBUFLFDDDURBLLRRRLUDLDUFFUBBULB")

        assertEquals(scrambled, classify(capturesOf(turned), params))
    }

    @Test
    fun colorsShiftedByTheLightAreClassified() {
        // Darker and warmer than the palette: only distances to the centers matter
        val scrambled = CubeState.of("DDLFUFFBBRLFDRBRRBUDULFRBUFLFDDDURBLLRRRLUDLDUFFUBBULB")
        val warm = { face: Face -> colorOf(face).let { Lab(it.l * 0.7, it.a + 15, it.b + 25) } }

        assertEquals(scrambled, classify(capturesOf(scrambled, warm), params))
    }

    @Test
    fun aStickerCloserToAWrongColorIsCorrectedByItsPiece() {
        val scrambled = CubeState.of("DDLFUFFBBRLFDRBRRBUDULFRBUFLFDDDURBLLRRRLUDLDUFFUBBULB")
        // F1 is white; seen 60 % of the way to yellow, it is nearer to yellow
        val white = colorOf(Face.U)
        val yellow = colorOf(Face.D)
        val misread = Lab(
            0.4 * white.l + 0.6 * yellow.l,
            0.4 * white.a + 0.6 * yellow.a,
            0.4 * white.b + 0.6 * yellow.b,
        )
        val captures = replaced(capturesOf(scrambled), idOf(Face.F, 0), misread)

        // A yellow sticker there would make a corner that does not exist
        assertEquals(scrambled, classify(captures, params))
    }

    @Test
    fun everyColorIsOn9FaceletsAndCentersAreStandard() {
        // Colors that fit no cube still give a legal state, white up and green in front
        val noise = java.util.Random(0)
        val random = { _: Face ->
            Lab(
                noise.nextDouble() * 30,
                noise.nextDouble() * 200 - 100, noise.nextDouble() * 200 - 100)
        }
        val solved = CubeState.of("UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB")

        val state = classify(capturesOf(solved, random), params)

        assertTrue(state.colors.groupingBy { it }.eachCount().values.all { it == 9 })
        assertEquals(Face.entries, Face.entries.map { state.colors[idOf(it, CENTER)] })
    }

    /** The captures with the color of one facelet replaced */
    private fun replaced(captures: List<List<Lab>>, id: Int, color: Lab): List<List<Lab>> {
        val face = FACELETS[id].face
        val k = PHOTO_ORDER.indexOf(face)
        val cell = faceView(face, PHOTO_TOP.getValue(face)).indexOf(FACELETS[id].index)
        return captures.mapIndexed { i, colors ->
            if (i == k) colors.mapIndexed { j, c -> if (j == cell) color else c } else colors
        }
    }
}
