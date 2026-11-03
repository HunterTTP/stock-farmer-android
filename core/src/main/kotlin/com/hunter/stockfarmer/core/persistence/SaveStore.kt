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

    /**
     * The hash of the bytes last read back whole or written here. It names bytes rather than a moment, so it cannot go
     * stale: a rollback, another writer or outside damage changes the file's hash, and a miss means a full read-back.
     */
    @Volatile private var knownGood: ByteArray? = null

    /** Null means no file. A file that can't be read, or is from a later version, throws and is left untouched. */
    fun load(): GameState? {
        val data = files.read(fileURL) ?: return null
        return decoded(data).also { knownGood = sha256(data) }
    }

    fun save(state: GameState) {
        state.validate()
        files.read(fileURL)?.let(::requireOpens)
        replace(state)
    }

    /**
     * A failed load must never become an ordinary overwrite. Length and modified time could not prove the file unchanged,
     * as timestamps are coarser than a save, but the caller holds the bytes for the backup anyway, and hashing them is
     * far cheaper than parsing them.
     */
    internal fun requireOpens(data: ByteArray) {
        val hash = sha256(data)
        if (knownGood?.contentEquals(hash) == true) return
        decoded(data)
        knownGood = hash
    }

    /** Trusts the caller to have validated [state]: its bytes count as good from here on, without being read back. */
    internal fun replace(state: GameState) {
        val text = GameStateJson.json.encodeToString(Envelope(CURRENT_SCHEMA_VERSION, state))
        val data = text.toByteArray(Charsets.UTF_8)
        files.write(data, fileURL)
        knownGood = sha256(data)
    }

    private fun decoded(data: ByteArray): GameState {
        val saved = GameStateJson.json.parseToJsonElement(data.toString(Charsets.UTF_8)).jsonObject
        val version = saved.getValue("schemaVersion").jsonPrimitive.int
        if (version > CURRENT_SCHEMA_VERSION) throw NewerSaveVersionException()
        return GameStateJson.decode(saved.getValue("state"))
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}
