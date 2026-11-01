package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.model.GameState
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SaveValidationTest {
    @ParameterizedTest
    @ValueSource(ints = [-1, 999])
    fun `decoration sizes are clamped on decode`(size: Int) {
        val placed = Json.decodeFromString<PlacedDecoration>("""{"decorationID":"pumpkin","size":$size}""")
        assertTrue(placed.size in placed.decoration.assetNames.indices)
    }

    @Test
    fun `an unknown decoration does not become an unrelated item`() {
        assertThrows(Exception::class.java) {
            Json.decodeFromString<PlacedDecoration>("""{"decorationID":"unknown-future-item","size":0}""")
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"money":-1}""",
            """{"xp":-1}""",
            """{"storage":{"wheat":-1}}""",
            """{"plots":"broken"}""",
            """{"farmland":[{"row":999,"col":0}]}""",
        ],
    )
    fun `malformed state is rejected instead of silently reset`(json: String) {
        assertThrows(Exception::class.java) { GameStateJson.decode(json) }
    }

    @Test
    fun `missing historical fields still use defaults`() {
        val state = GameStateJson.decode("""{"money":12.5}""")
        assertEquals(12.5, state.money)
        assertEquals(GameState.startingFarmland, state.farmland)
        assertTrue(state.day == 1 && state.lingeringWater.isEmpty())
    }
}
