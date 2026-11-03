package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

/** Reads and writes a farm as JSON. A loaded farm has its derived indexes rebuilt. */
object GameStateJson {
    internal val json =
        Json {
            allowStructuredMapKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    fun decode(text: String): GameState = decode(json.parseToJsonElement(text))

    internal fun decode(saved: JsonElement): GameState =
        json.decodeFromJsonElement(GameState.serializer(), saved.jsonObject).also {
            it.finishDecoding()
            it.validate()
            it.reindex()
        }

    /**
     * Names everything a farm holds except what moves while nothing is happening: the time played and when it was last
     * saved. It reads the encoded farm, so a field added later counts without anyone having to remember it. Equal maps in
     * another order hash differently, which costs one needless write and loses nothing.
     */
    internal fun contentHash(state: GameState): ByteArray {
        val still =
            state.saveSnapshot().apply {
                playTime = 0.0
                lastSavedAt = null
            }
        return sha256(encode(still).toByteArray(Charsets.UTF_8))
    }
}

internal fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
