package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import kotlinx.serialization.json.Json

/** Reads and writes a farm as JSON. A loaded farm has its derived indexes rebuilt. */
object GameStateJson {
    internal val json =
        Json {
            allowStructuredMapKeys = true
            encodeDefaults = true
        }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    fun decode(text: String): GameState = json.decodeFromString(GameState.serializer(), text).also { it.reindex() }
}
