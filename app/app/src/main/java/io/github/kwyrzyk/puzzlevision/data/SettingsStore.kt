package io.github.kwyrzyk.puzzlevision.data


import io.github.kwyrzyk.puzzlevision.vision.Params
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class SettingsStore(directory: File) {
    private val file = File(directory, "settings.json")
    private val temporary = File(directory, "settings.json.tmp")
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /** Saved parameters; defaults when none or invalid **/
    fun load(): Params {
        if (!file.exists()) return Params()
        return try {
            json.decodeFromString<Params>(file.readText()).takeIf { it.validate() == null }
                ?: Params()
        } catch (_: SerializationException) {
            Params()
        }
    }

    /** Replace file atomically to avoid semi-saved state */
    fun save(params: Params) {
        temporary.writeText(json.encodeToString(params))
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}