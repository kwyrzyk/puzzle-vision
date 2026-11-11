package io.github.kwyrzyk.puzzlevision.data

import io.github.kwyrzyk.puzzlevision.vision.FrameTrace
import io.github.kwyrzyk.puzzlevision.vision.Lab
import io.github.kwyrzyk.puzzlevision.vision.Params
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A captured face; times in ms of the camera clock */
@Serializable
data class SavedFace(val first: Long, val last: Long, val colors: List<Lab>)

/** What a frame of a session ended with; the JSON values are those of notebook 3.3 */
@Serializable
enum class FrameEvent {
    @SerialName("capture") CAPTURE,
    @SerialName("duplicate") DUPLICATE,
}

/**
 * One processed frame, a row of the timeline of notebook 3.3 with its column names.
 * Metrics of the candidate with the smallest align; null without one.
 */
@Serializable
data class SavedFrame(
    val t: Long, // ms of the camera clock
    val size: Double? = null,
    val align: Double? = null,
    val tilt: Double? = null,
    val skew: Double? = null,
    val rotation: Double? = null,
    val motion: Double? = null,
    @SerialName("color") val colorDrift: Double? = null,
    val window: Boolean = false,
    val event: FrameEvent? = null,
)

fun savedFrameOf(trace: FrameTrace) = SavedFrame(
    t = trace.t,
    size = trace.metrics?.size,
    align = trace.metrics?.align,
    tilt = trace.metrics?.tilt,
    skew = trace.metrics?.skew,
    rotation = trace.metrics?.rotation,
    motion = trace.motion,
    colorDrift = trace.colorDrift,
    window = trace.window,
    event = trace.capture?.let { if (it.duplicate) FrameEvent.DUPLICATE else FrameEvent.CAPTURE },
)

/** One complete session, for the Detections screen and for replay in research */
@Serializable
data class Session(
    @SerialName("started_at") val startedAt: Long, // ms since 1970
    val params: Params,
    val faces: List<SavedFace>, // 6 saved faces in PHOTO_ORDER
    val state: String, // Kociemba's facelet string
    val frames: List<SavedFrame> = emptyList(), // none in sessions saved before step 8
)

/** Sessions as JSON files in `sessions/`, one per session, named by its start */
class SessionStore(directory: File) {
    private val folder = File(directory, "sessions")
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /** Atomic save to avoid half-written file */
    fun save(session: Session) {
        folder.mkdirs()
        val file = File(folder, "${session.startedAt}.json")
        val temporary = File(folder, "${session.startedAt}.json.tmp")
        temporary.writeText(json.encodeToString(session))
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    /** Saved sessions, the newest first; files that cannot be read are skipped */
    fun load(): List<Session> =
        folder.listFiles().orEmpty()
            .filter { it.extension == "json" }
            .mapNotNull { file ->
                try {
                    json.decodeFromString<Session>(file.readText())
                } catch (_: SerializationException) {
                    null
                }
            }
            .sortedByDescending { it.startedAt }
}
