package io.github.kwyrzyk.puzzlevision.data

import io.github.kwyrzyk.puzzlevision.vision.Params
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "settings.json")
    private val store: SettingsStore get() = SettingsStore(folder.root)

    @Test
    fun defaultsWithoutFile() {
        assertEquals(Params(), store.load())
    }

    @Test
    fun savedValuesAreLoaded() {
        val params = Params(width = 450, hull = true, showBox = 0.7)
        store.save(params)
        store.save(params.copy(width = 360))
        assertEquals(params.copy(width = 360), store.load())
    }

    @Test
    fun fileUsesNotebookNames() {
        store.save(Params())
        val text = file.readText()
        assertTrue(text.contains("\"canny_low\": 40"))
        assertTrue(text.contains("\"hold_ms\": 300"))
    }

    @Test
    fun missingKeysGetDefaultsAndUnknownKeysAreIgnored() {
        file.writeText("""{"width": 360, "removed_param": 1}""")
        assertEquals(Params(width = 360), store.load())
    }

    @Test
    fun brokenFileGivesDefaults() {
        file.writeText("""{"width": """)
        assertEquals(Params(), store.load())
    }

    @Test
    fun invalidValuesGivesDefaults() {
        file.writeText("""{"blur": 4 }""")
        assertEquals(Params(), store.load())
    }
}