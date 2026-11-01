package com.hunter.stockfarmer.core.catalog

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroundCoverTest {
    @Test fun `dirtAndCobblestonePathsCostTwoDollarsATile`() {
        assertEquals(listOf("dirtPath", "cobblestonePath"), GroundCover.entries.map { it.rawValue })
        assertEquals(listOf("Dirt Path", "Cobblestone Path"), GroundCover.entries.map { it.displayName })
        assertTrue(GroundCover.entries.all { it.cost == 2.0 })
    }

    @Test fun `retired cover identifiers decode as dirt paths`() {
        for (id in listOf("dirtPath", "sandPath", "raisedBed", "barnFloor", "stableFloor")) {
            assertEquals(GroundCover.dirtPath, Json.decodeFromString<GroundCover>("\"$id\""))
        }
        assertEquals(GroundCover.cobblestonePath, Json.decodeFromString<GroundCover>("\"cobblestonePath\""))
        assertThrows(SerializationException::class.java) { Json.decodeFromString<GroundCover>("\"unknown\"") }
    }
}
