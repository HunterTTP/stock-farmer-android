package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.LivestockHome
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.util.advancedBy
import java.time.Instant

/**
 * The late farm of the performance audit, always laid out the same: 650 ripe plots of every favorite crop inside a closed
 * fence, 300 water, 200 trees, 50 rocks, 200 fences and 60 animals, of which the 52 that eat are hungry and shut out of the
 * field: each searches all the ground it can reach and finds no way to the food.
 */
internal object LateFarm {
    val start: Instant = Instant.ofEpochSecond(50_000_000)
    const val MORNING = 8.0 * 60
    val gate = GridPoint(11, 24)

    private val favorites = listOf("carrot", "potato", "lettuce", "cabbage", "broccoli", "zucchini", "beetroot")
    private val pond = GridPoint(41, 3)
    private const val DAY = 86_400.0

    fun build(
        now: Instant = start,
        minute: Double = MORNING,
    ): GameEngine {
        val state = GameState()
        val engine = GameEngine(state)
        state.xp = lateGameXP
        engine.setTimeOfDay(minute, now)
        engine.bonusRoll = { 0.5 }
        field(state, now)
        water(state)
        fences(state)
        trees(state, now)
        rocks(state)
        for (col in listOf(12, 20, 28, 36)) state.putDecoration(GridPoint(10, col), PlacedDecoration("scarecrow"))
        herds(state, now)
        coops(state, now)
        pets(state)
        ducks(state, now)
        return engine
    }

    private fun field(
        state: GameState,
        now: Instant,
    ) {
        state.farmland = mutableSetOf()
        for (row in 12..37) {
            for (col in 12..36) {
                val tile = GridPoint(row, col)
                state.farmland.add(tile)
                state.plots[tile] = Plot(favorites[(row + col) % favorites.size], now.advancedBy(-30 * DAY))
            }
        }
    }

    private fun water(state: GameState) {
        for (row in 40..49) {
            for (col in 2..31) state.water[GridPoint(row, col)] = WaterStyle.deep
        }
    }

    private fun fences(state: GameState) {
        val tiles =
            (11..37).flatMap { listOf(GridPoint(11, it), GridPoint(38, it)) } +
                (12..37).flatMap { listOf(GridPoint(it, 11), GridPoint(it, 37)) } +
                (0..31).map { GridPoint(39, it) } +
                (38..49).map { GridPoint(39, it) } +
                (12..38).map { GridPoint(it, 1) } +
                (12..34).map { GridPoint(it, 49) }
        for (tile in tiles) state.fences[tile] = FenceColor.starter.id
    }

    private fun trees(
        state: GameState,
        now: Instant,
    ) {
        val sides = listOf(3, 5, 7, 9, 39, 41, 43, 45, 47)
        val spots =
            (12..38 step 2).flatMap { row -> sides.map { GridPoint(row, it) } } +
                (40..48 step 2).flatMap { row -> (33..47 step 2).map { GridPoint(row, it) } } +
                (13..37 step 2).flatMap { row -> listOf(4, 8, 40, 44).map { GridPoint(row, it) } }
        for (spot in spots.take(200)) state.putTree(spot, PlacedTree(Tree.starter, TreeColor.green, now))
    }

    private fun rocks(state: GameState) {
        val spots =
            (41..49 step 2).flatMap { row -> (34..48 step 2).map { GridPoint(row, it) } } +
                (13..31 step 2).map { GridPoint(it, 6) }
        for (spot in spots) state.rocks[spot] = RockSize.entries.first()
    }

    private fun herds(
        state: GameState,
        now: Instant,
    ) {
        val cols = listOf(1, 6, 12, 18, 24, 32)
        for ((index, home) in LivestockHome.all.withIndex()) {
            val building = Building.named(home.buildingID)!!
            val origin = GridPoint(6 - building.height, cols[index])
            state.putBuilding(origin, home.buildingID)
            state.homeLevels[origin] = 2
            val species = home.species[0]
            val animals =
                (0 until 7).map { id ->
                    Livestock.born(
                        id,
                        species.variants[id % species.variants.size],
                        species.name,
                        GridPoint(7 + id % 3, cols[index] + id),
                        now.advancedBy(-30 * DAY),
                    )
                }
            state.herds[origin] = Herd(animals, now.advancedBy(home.birthInterval), now, animals.size)
        }
    }

    private fun coops(
        state: GameState,
        now: Instant,
    ) {
        for ((index, col) in listOf(43, 45).withIndex()) {
            val origin = GridPoint(3, col)
            state.putBuilding(origin, "chickenCoop")
            state.homeLevels[origin] = 3
            val hens =
                (0 until 5).map { id ->
                    Chicken.hatched(
                        id,
                        ChickenVariant.all[id],
                        Chicken.DEFAULT_NAME,
                        GridPoint(8 + index, col - 2 + id),
                        now.advancedBy(-30 * DAY),
                    )
                }
            state.coops[origin] = Coop(chickens = hens, nextChickAt = now.advancedBy(DAY), nextChickenID = hens.size)
        }
    }

    private fun pets(state: GameState) {
        for (col in listOf(0, 47, 48, 49)) {
            val house = GridPoint(4, col)
            state.putBuilding(house, "petHouse")
            state.pets[house] = Pet(PetSpecies.cat, Cat.starter.id, "Pet", GridPoint(7, col))
        }
    }

    private fun ducks(
        state: GameState,
        now: Instant,
    ) {
        val species = LivestockHome.pond.species[0]
        val spots = listOf(GridPoint(43, 5), GridPoint(43, 15), GridPoint(46, 10), GridPoint(46, 25))
        val animals =
            spots.mapIndexed { id, spot ->
                Livestock.born(id, species.variants[0], species.name, spot, now.advancedBy(-30 * DAY))
            }
        state.herds[pond] = Herd(animals, now.advancedBy(LivestockHome.pond.birthInterval), now, animals.size)
    }
}
