package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TractorNavigationTest {
    private val home = GridPoint(10, 10)
    private val start = GridPoint(10, 12)
    private val crop = GridPoint(10, 20)

    private fun navigation(state: GameState) = TractorNavigation(state, home)

    private fun laneOfWater(state: GameState) {
        for (col in 0 until GameState.GRID_SIZE) {
            state.water[GridPoint(9, col)] = WaterStyle.deep
            state.water[GridPoint(11, col)] = WaterStyle.deep
        }
    }

    @Test
    fun `driving and parked footprints agree in every direction`() {
        val tractor = Building.named("tractor1")!!
        for (facing in TractorFacing.entries) {
            val horizontal = facing.direction.col != 0
            val expected = home.rectangle(if (horizontal) 1 else 2, if (horizontal) 2 else 1).toSet()
            val head = TractorRules.headTile(home, facing)
            assertEquals(expected, TractorRules.footprint(head, facing).toSet())
            assertEquals(expected, tractor.styled(facing.styleID).footprint(home).toSet())
            assertEquals(home, TractorRules.origin(head, facing))
            for (tile in expected) {
                val state = GameState()
                state.rocks[tile] = RockSize.small
                assertFalse(navigation(state).canEnter(head, facing))
            }
        }
    }

    @Test
    fun `horizontal tractor can fit in a single row lane`() {
        val state = GameState()
        laneOfWater(state)
        assertNotNull(navigation(state).route(start, TractorFacing.right, setOf(crop)))
    }

    @Test
    fun `shorter footprint leaves the third tile available in every direction`() {
        for (facing in TractorFacing.entries) {
            val horizontal = facing.direction.col != 0
            val third = GridPoint(home.row + (if (horizontal) 0 else 2), home.col + (if (horizontal) 2 else 0))
            val state = GameState()
            state.putBuilding(home, "tractor1", facing.styleID)
            assertNull(state.buildingBlocking(third))
            state.rocks[third] = RockSize.small
            val head = TractorRules.headTile(home, facing)
            assertTrue(navigation(state).canEnter(head, facing))
        }
    }

    @Test
    fun `prefers crops on the connected farmland over nearer separated farmland`() {
        val state = GameState()
        val here = GridPoint(20, 20)
        val connected = (0 until 10).map { GridPoint(20, 20 + it) }
        val nearbyIsland = GridPoint(20, 16)
        state.farmland = (connected + nearbyIsland).toMutableSet()
        val route = navigation(state).route(here, TractorFacing.right, setOf(GridPoint(20, 29), nearbyIsland))
        assertEquals(GridPoint(20, 29), route?.last())
    }

    @Test
    fun `falls back to separated farmland when the connected farmland has no crops`() {
        val state = GameState()
        val here = GridPoint(20, 20)
        val island = GridPoint(20, 14)
        state.farmland = ((0 until 3).map { GridPoint(20, 20 + it) } + island).toMutableSet()
        val route = navigation(state).route(here, TractorFacing.right, setOf(island))
        assertEquals(island, route?.last())
    }

    @Test
    fun `crosses farmland when it is the only way to the crop or home`() {
        val state = GameState()
        val field = GridPoint(10, 13)
        state.farmland = mutableSetOf(field, crop)
        laneOfWater(state)
        val navigation = navigation(state)
        val outward = navigation.route(start, TractorFacing.right, setOf(crop))!!
        assertTrue(field in outward && outward.last() == crop)
        val returning = navigation.route(crop, TractorFacing.right, home = home)!!
        assertTrue(field in returning)
    }

    @Test
    fun `drives straight across farmland when that is the shortest way`() {
        val state = GameState()
        val field = (13..17).map { GridPoint(10, it) }.toSet()
        state.farmland = (field + crop).toMutableSet()
        for (col in 11..20) state.groundCover[GridPoint(9, col)] = GroundCover.dirtPath
        val navigation = navigation(state)
        val outward = navigation.route(start, TractorFacing.right, setOf(crop))!!
        assertTrue(outward.all { it.row == start.row } && outward.last() == crop)
        val returning = navigation.route(crop, TractorFacing.right, home = home)!!
        assertTrue(returning.all { it.row == start.row })
        val destination = returning.last()
        val before = returning.dropLast(1).lastOrNull() ?: crop
        val facing = TractorFacing.ofDirection(GridPoint(destination.row - before.row, destination.col - before.col))
        assertEquals(home.rectangle(1, 2).toSet(), TractorRules.footprint(destination, facing).toSet())
    }

    @Test
    fun `finishes an L shaped plot before a nearer separated one`() {
        val state = GameState()
        val here = GridPoint(20, 20)
        val plot = (0 until 6).map { GridPoint(20, 20 + it) } + (1 until 5).map { GridPoint(20 + it, 25) }
        val island = GridPoint(22, 17)
        state.farmland = (plot + island).toMutableSet()
        val route = navigation(state).route(here, TractorFacing.right, setOf(GridPoint(24, 25), island))
        assertEquals(GridPoint(24, 25), route?.last())
    }

    @Test
    fun `plots touching only at a corner are separate but ones touching along a side are one`() {
        val here = GridPoint(20, 20)
        val farEnd = GridPoint(20, 23)
        val underneath = GridPoint(21, 20)
        val diagonal = GridPoint(21, 19)
        for ((neighbor, expected) in listOf(underneath to underneath, diagonal to farEnd)) {
            val state = GameState()
            state.farmland = ((0 until 4).map { GridPoint(20, 20 + it) } + neighbor).toMutableSet()
            val route = navigation(state).route(here, TractorFacing.right, setOf(farEnd, neighbor))
            assertEquals(expected, route?.last())
        }
    }
}
