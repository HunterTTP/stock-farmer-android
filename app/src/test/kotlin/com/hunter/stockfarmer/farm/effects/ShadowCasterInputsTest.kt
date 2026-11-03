package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.farm.MovingOrigins
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant

internal class ShadowCasterInputsTest {
    private val at = Instant.ofEpochSecond(50_000)
    private val inputs = ShadowCasterInputs()
    private val state =
        GameState().apply {
            putBuilding(GridPoint(20, 20), "chickenCoop")
            putTree(GridPoint(10, 10), PlacedTree(Tree.all[3], TreeColor.darkGreen, at))
            rocks[GridPoint(5, 5)] = RockSize.small
            putDecoration(GridPoint(8, 8), PlacedDecoration("stool"))
            fences[GridPoint(30, 30)] = "white"
            plots[GridPoint(35, 35)] = Plot(fruit.id, at)
            plots[GridPoint(36, 36)] = Plot(vegetable.id, at)
        }

    private fun settled() =
        assertTrue(inputs.changed(state, MovingOrigins.NONE, 1), "the first look finds casters to make")

    @Test
    fun `the same farm at the same sun step needs no rebuild, frame after frame`() {
        settled()
        repeat(3) { assertFalse(inputs.changed(state, MovingOrigins.NONE, 1)) }
    }

    @Test
    fun `a new sun step rebuilds`() {
        settled()
        assertTrue(inputs.changed(state, MovingOrigins.NONE, 2))
        assertFalse(inputs.changed(state, MovingOrigins.NONE, 2))
    }

    @Test
    fun `things being carried rebuild, going and coming back`() {
        settled()
        val spot = GridPoint(10, 10)
        for (moving in listOf(
            MovingOrigins(tree = spot),
            MovingOrigins(building = spot),
            MovingOrigins(decoration = spot),
            MovingOrigins(fruitTree = spot),
        )) {
            assertTrue(inputs.changed(state, moving, 1), "$moving")
            assertFalse(inputs.changed(state, moving, 1), "$moving")
            assertTrue(inputs.changed(state, MovingOrigins.NONE, 1), "$moving put back")
        }
    }

    @Test
    fun `a building moved keeps nothing stale but a repaint casts the same shadow`() {
        settled()
        state.removeBuilding(GridPoint(20, 20))
        state.putBuilding(GridPoint(21, 20), "chickenCoop")
        assertTrue(inputs.changed(state, MovingOrigins.NONE, 1))
        state.putBuilding(GridPoint(21, 20), "chickenCoop", "blue")
        assertFalse(inputs.changed(state, MovingOrigins.NONE, 1))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shadowChanges")
    fun `every input the casters read invalidates`(
        name: String,
        change: GameState.() -> Unit,
    ) {
        settled()
        state.change()
        assertTrue(inputs.changed(state, MovingOrigins.NONE, 1), name)
        assertFalse(inputs.changed(state, MovingOrigins.NONE, 1), "$name, once rebuilt")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unrelatedChanges")
    fun `what casts no shadow does not invalidate`(
        name: String,
        change: GameState.() -> Unit,
    ) {
        settled()
        state.change()
        assertFalse(inputs.changed(state, MovingOrigins.NONE, 1), name)
    }

    @Test
    fun `a same-sized swap between kinds of object is seen`() {
        settled()
        state.rocks.remove(GridPoint(5, 5))
        state.fences[GridPoint(5, 5)] = "white"
        assertTrue(inputs.changed(state, MovingOrigins.NONE, 1))
    }

    @Test
    fun `an emptied farm is seen even though nothing differs in what remains`() {
        settled()
        state.fences.clear()
        assertTrue(inputs.changed(state, MovingOrigins.NONE, 1))
    }

    @Test
    fun `a large farm that outgrows the buffers still compares exactly`() {
        for (row in 0 until 50) {
            for (col in 0 until 20) state.fences[GridPoint(row, col)] = "white"
        }
        settled()
        assertFalse(inputs.changed(state, MovingOrigins.NONE, 1))
        state.fences.remove(GridPoint(49, 19))
        assertTrue(inputs.changed(state, MovingOrigins.NONE, 1))
    }

    private companion object {
        val fruit = Crop.all.first { it.isFruitTree }
        val vegetable = Crop.all.first { !it.isFruitTree }
        val when0: Instant = Instant.ofEpochSecond(50_000)

        @JvmStatic
        fun shadowChanges() =
            listOf(
                arguments("season") { season = Season.entries.first { it != season } },
                arguments("new building") { putBuilding(GridPoint(25, 25), "petHouse") },
                arguments("removed building") { removeBuilding(GridPoint(20, 20)) },
                arguments("tractor out") {
                    tractorRun =
                        TractorRun(
                            GridPoint(20, 20),
                            vegetable.id,
                            GridPoint(21, 21),
                            facing = TractorFacing.entries[0],
                        )
                },
                arguments(
                    "new tree",
                ) { putTree(GridPoint(11, 11), PlacedTree(Tree.all[3], TreeColor.darkGreen, when0)) },
                arguments("removed tree") { removeTree(GridPoint(10, 10)) },
                arguments(
                    "recolored tree",
                ) { putTree(GridPoint(10, 10), PlacedTree(Tree.all[4], TreeColor.darkGreen, when0)) },
                arguments("new rock") { rocks[GridPoint(6, 6)] = RockSize.big },
                arguments("rock grown") { rocks[GridPoint(5, 5)] = RockSize.big },
                arguments("removed rock") { rocks.remove(GridPoint(5, 5)) },
                arguments("new decoration") { putDecoration(GridPoint(9, 9), PlacedDecoration("stool")) },
                arguments("resized decoration") { putDecoration(GridPoint(8, 8), PlacedDecoration("stool", 1)) },
                arguments("removed decoration") { removeDecoration(GridPoint(8, 8)) },
                arguments("new fence") { fences[GridPoint(30, 31)] = "white" },
                arguments("removed fence") { fences.remove(GridPoint(30, 30)) },
                arguments("new fruit tree") { plots[GridPoint(37, 37)] = Plot(fruit.id, when0) },
                arguments("removed fruit tree") { plots.remove(GridPoint(35, 35)) },
            )

        @JvmStatic
        fun unrelatedChanges() =
            listOf(
                arguments("money") { money = 123.45 },
                arguments("day") { day += 1 },
                arguments("clock") { clockOffset += 3 },
                arguments("fence recolored") { fences[GridPoint(30, 30)] = "black" },
                arguments("crop planted") { plots[GridPoint(38, 38)] = Plot(vegetable.id, when0) },
                arguments("crop harvested") { plots.remove(GridPoint(36, 36)) },
                arguments("crop eaten at") { plots[GridPoint(36, 36)] = Plot(vegetable.id, when0, servingsEaten = 1) },
                arguments(
                    "fruit tree eaten at",
                ) { plots[GridPoint(35, 35)] = Plot(fruit.id, when0, servingsEaten = 1) },
                arguments("farmland") { farmland.add(GridPoint(1, 1)) },
            )

        private fun arguments(
            name: String,
            change: GameState.() -> Unit,
        ) = org.junit.jupiter.params.provider.Arguments
            .of(name, change)
    }
}
