package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Reads and writes a farm as JSON. A loaded farm has its derived indexes rebuilt. */
object GameStateJson {
    internal val json =
        Json {
            allowStructuredMapKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    fun decode(text: String): GameState {
        val saved = json.parseToJsonElement(text).jsonObject
        return json.decodeFromJsonElement(GameState.serializer(), saved.prepareGameState()).also {
            it.finishDecoding(saved)
            it.validate()
            it.keepBottomRowPlots()
            it.reindex()
        }
    }
}
