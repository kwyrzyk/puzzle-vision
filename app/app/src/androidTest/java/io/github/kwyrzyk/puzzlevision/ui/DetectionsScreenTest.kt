package io.github.kwyrzyk.puzzlevision.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kwyrzyk.puzzlevision.data.SavedFace
import io.github.kwyrzyk.puzzlevision.data.Session
import io.github.kwyrzyk.puzzlevision.vision.Lab
import io.github.kwyrzyk.puzzlevision.vision.Params
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetectionsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val solved = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB"
    private val scrambled = "DDLFUFFBBRLFDRBRRBUDULFRBUFLFDDDURBLLRRRLUDLDUFFUBBULB"

    private fun sessionAt(startedAt: Long, state: String) = Session(
        startedAt = startedAt,
        params = Params(),
        faces = List(6) { SavedFace(0, 300, List(9) { Lab(30.0, 0.0, 0.0) }) },
        state = state,
    )

    private val older = sessionAt(1_762_000_000_000, solved)
    private val newer = sessionAt(1_762_100_000_000, scrambled)
    private var backs = 0

    private fun show(sessions: List<Session>) =
        compose.setContent { DetectionsScreen(sessions, onBack = { backs++ }) }

    @Test
    fun withoutSessionsTheListSaysSo() {
        show(emptyList())

        compose.onNodeWithText("No detections yet").assertExists()
    }

    @Test
    fun everySessionIsListedByItsDate() {
        show(listOf(newer, older))

        compose.onNodeWithText(dateOf(newer.startedAt)).assertExists()
        compose.onNodeWithText(dateOf(older.startedAt)).assertExists()
    }

    @Test
    fun aTapOpensTheSession() {
        show(listOf(newer, older))

        compose.onNodeWithText(dateOf(older.startedAt)).performClick()

        compose.onNodeWithText(solved).assertExists()
        compose.onNodeWithText("Captured colors").assertExists()
        compose.onNodeWithText(dateOf(newer.startedAt)).assertDoesNotExist()
    }

    @Test
    fun backFromASessionReturnsToTheList() {
        show(listOf(newer, older))
        compose.onNodeWithText(dateOf(newer.startedAt)).performClick()

        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithText(dateOf(older.startedAt)).assertExists()
        assertEquals(0, backs)
    }

    @Test
    fun backFromTheListLeavesTheScreen() {
        show(listOf(newer))

        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }
}
