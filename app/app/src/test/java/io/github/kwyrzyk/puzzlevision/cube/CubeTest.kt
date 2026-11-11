package io.github.kwyrzyk.puzzlevision.cube

import org.junit.Assert.assertEquals
import org.junit.Test

class CubeTest {
    @Test
    fun faceletsAreNumberedOnTheNet() {
        assertEquals(54, FACELETS.size)
        assertEquals(Facelet(Face.U, 0, Vector(-1, 1, -1)), FACELETS[0]) // back left corner of U
        assertEquals(Facelet(Face.F, 4, Vector(0, 0, 1)), FACELETS[idOf(Face.F, CENTER)])
        assertEquals(Facelet(Face.D, 8, Vector(1, -1, -1)), FACELETS[idOf(Face.D, 8)])
        assertEquals(Face.B, FACELETS[53].face) // faces in the order U, R, F, D, L, B
    }

    @Test
    fun faceSeenWithItsNetTopIsInNetOrder() {
        assertEquals((0..8).toList(), faceView(Face.U, Face.B))
        assertEquals((0..8).toList(), faceView(Face.L, Face.U))
    }

    @Test
    fun faceSeenTurnedIsRotated() {
        // Values of face_view in puzzle_vision.cube
        assertEquals(listOf(2, 5, 8, 1, 4, 7, 0, 3, 6), faceView(Face.D, Face.R))
        assertEquals(listOf(2, 5, 8, 1, 4, 7, 0, 3, 6), faceView(Face.F, Face.R))
    }

    @Test
    fun piecePlacesCoverEveryFaceletButTheCentersOnce() {
        val ids = (CORNERS + EDGES).flatMap(::placeOf)

        assertEquals(48, ids.size)
        assertEquals(48, ids.toSet().size)
        val centers = Face.entries.map { idOf(it, CENTER) }.toSet()
        assertEquals(centers, (0 until 54).toSet() - ids.toSet())
    }

    @Test
    fun placeOfAPieceIsInTheOrderOfItsName() {
        // URF: U9, R1, F3
        assertEquals(listOf(idOf(Face.U, 8), idOf(Face.R, 0), idOf(Face.F, 2)), placeOf("URF"))
    }

    @Test
    fun stateIsWrittenAsAFaceletString() {
        val solved = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB"

        assertEquals(solved, CubeState.of(solved).toString())
    }

    @Test
    fun standardCubeKeepsItsOrientation() {
        val scrambled = CubeState.of(SCRAMBLED)

        assertEquals(scrambled, scrambled.standardOrientation())
    }

    @Test
    fun turnedCubeIsTurnedToWhiteUpGreenFront() {
        // SCRAMBLED turned about the R axis: green up, white at the back
        val turned = CubeState.of("BLUBBUFFUFBBLRRRDRDDLFUFFBBUDULFRBUFDRLLLRDURLBRUDDDFL")

        assertEquals(CubeState.of(SCRAMBLED), turned.standardOrientation())
    }

    private companion object {
        // R U R' U' F2 D L' B2 U2 R D' F, from CubeState.from_scramble
        const val SCRAMBLED = "DDLFUFFBBRLFDRBRRBUDULFRBUFLFDDDURBLLRRRLUDLDUFFUBBULB"
    }
}
