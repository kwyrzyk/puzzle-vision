package io.github.kwyrzyk.puzzlevision.data

import io.github.kwyrzyk.puzzlevision.vision.Lab
import io.github.kwyrzyk.puzzlevision.vision.Params
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store: SessionStore get() = SessionStore(folder.root)
    private val sessions: File get() = File(folder.root, "sessions")

    private fun sessionAt(startedAt: Long) = Session(
        startedAt = startedAt,
        params = Params(width = 360),
        faces = List(6) { k ->
            SavedFace(1000L * k, 1000L * k + 300, List(9) { Lab(30.0, k.toDouble(), -1.5) })
        },
        state = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB",
        frames = listOf(
            SavedFrame(t = 0),
            SavedFrame(t = 100, size = 1.1, align = 0.05, motion = 0.02, window = true),
            SavedFrame(t = 200, colorDrift = 1.5, window = true, event = FrameEvent.CAPTURE),
        ),
    )

    @Test
    fun noSessionsWithoutFolder() {
        assertEquals(emptyList<Session>(), store.load())
    }

    @Test
    fun savedSessionIsLoaded() {
        store.save(sessionAt(5))

        assertEquals(listOf(sessionAt(5)), store.load())
    }

    @Test
    fun sessionsAreLoadedNewestFirst() {
        store.save(sessionAt(5))
        store.save(sessionAt(7))
        store.save(sessionAt(6))

        assertEquals(listOf(7L, 6L, 5L), store.load().map { it.startedAt })
    }

    @Test
    fun eachSessionHasItsOwnFile() {
        store.save(sessionAt(5))
        store.save(sessionAt(7))

        assertEquals(setOf("5.json", "7.json"), sessions.list()!!.toSet())
    }

    @Test
    fun fileUsesResearchNames() {
        store.save(sessionAt(5))

        val text = File(sessions, "5.json").readText()
        assertTrue(text.contains("\"started_at\": 5"))
        assertTrue(text.contains("\"canny_low\": 40")) // params as in settings.json
        assertTrue(text.contains("\"l\": 30.0"))
    }

    @Test
    fun brokenFilesAreSkipped() {
        store.save(sessionAt(5))
        File(sessions, "6.json").writeText("""{"started_at": """)
        File(sessions, "notes.txt").writeText("not a session")

        assertEquals(listOf(5L), store.load().map { it.startedAt })
    }

    @Test
    fun frameUsesNotebookColumns() {
        store.save(sessionAt(5))

        val text = File(sessions, "5.json").readText()
        assertTrue(text.contains("\"color\": 1.5"))
        assertTrue(text.contains("\"event\": \"capture\""))
    }

    @Test
    fun sessionSavedBeforeFramesIsLoadedWithoutThem() {
        store.save(sessionAt(5))
        val file = File(sessions, "5.json")
        val withoutFrames = Regex(""",\s*"frames": \[.*]""", RegexOption.DOT_MATCHES_ALL)
        file.writeText(file.readText().replace(withoutFrames, ""))

        assertEquals(listOf(sessionAt(5).copy(frames = emptyList())), store.load())
    }
}
