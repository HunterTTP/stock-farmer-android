package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.farm.MovingOrigins
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ShadowCastersTest {
    private val tractorHome = GridPoint(10, 10)
    private val siloOrigin = GridPoint(20, 20)

    private fun farm() =
        GameState().apply {
            putBuilding(tractorHome, "tractor1")
            putBuilding(siloOrigin, "silo")
        }

    @Test
    fun `every building casts a shadow while the Tractor is parked`() {
        assertEquals(setOf(tractorHome, siloOrigin), farm().shadowCastingBuildings().map { it.origin }.toSet())
    }

    @Test
    fun `a Tractor out driving leaves its home without a shadow`() {
        val state = farm()
        state.tractorRun =
            TractorRun(home = tractorHome, cropID = "wheat", position = GridPoint(30, 30), facing = TractorFacing.right)
        assertEquals(listOf(siloOrigin), state.shadowCastingBuildings().map { it.origin })
    }

    @Test
    fun `a building being moved casts no shadow from where it stood`() {
        assertEquals(
            listOf(tractorHome),
            farm().shadowCastingBuildings(MovingOrigins(building = siloOrigin)).map { it.origin },
        )
    }

    @Test
    fun `a moved decoration or tree at a building's tile leaves the building's shadow alone`() {
        val moving = MovingOrigins(decoration = siloOrigin, tree = tractorHome, fruitTree = siloOrigin)
        assertEquals(setOf(tractorHome, siloOrigin), farm().shadowCastingBuildings(moving).map { it.origin }.toSet())
    }
}
