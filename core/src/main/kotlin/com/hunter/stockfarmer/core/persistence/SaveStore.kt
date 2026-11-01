package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path

/** The file was written by a later version of the game, which this one can't read safely. */
class NewerSaveVersionException : Exception("The save was written by a newer version of the game")

/** A backup that is not there cannot be restored. */
class SaveRestoreException : Exception("No such backup")

/** One game's save file. */
class FileSaveStore(
    private val fileURL: Path,
    private val files: SaveFileSystem = LocalSaveFileSystem(),
) {
    @Serializable
    private class Envelope(
        val schemaVersion: Int,
        val state: GameState,
    )

    /** Null means no file. A file that can't be read, or is from a later version, throws and is left untouched. */
    fun load(): GameState? {
        val data = files.read(fileURL)?.toString(Charsets.UTF_8) ?: return null
        val version =
            GameStateJson.json
                .parseToJsonElement(data)
                .jsonObject
                .getValue("schemaVersion")
                .jsonPrimitive.int
        if (version > CURRENT_SCHEMA_VERSION) throw NewerSaveVersionException()
        return GameStateJson.json
            .decodeFromString<Envelope>(data)
            .state
            .also { it.reindex() }
    }

    fun save(state: GameState) {
        load() // A failed load must never become an ordinary overwrite.
        replace(state)
    }

    internal fun replace(state: GameState) {
        val text = GameStateJson.json.encodeToString(Envelope(CURRENT_SCHEMA_VERSION, state))
        files.write(text.toByteArray(Charsets.UTF_8), fileURL)
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}
