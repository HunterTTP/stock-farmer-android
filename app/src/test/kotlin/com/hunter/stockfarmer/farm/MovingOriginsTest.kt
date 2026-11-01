package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.beginMoving
import com.hunter.stockfarmer.core.rules.beginMovingDecoration
import com.hunter.stockfarmer.core.rules.beginMovingFruitTree
import com.hunter.stockfarmer.core.rules.beginMovingTree
import com.hunter.stockfarmer.core.rules.beginPlacing
import com.hunter.stockfarmer.core.rules.cancelPlacement
import com.hunter.stockfarmer.core.rules.nudgePlacement
import com.hunter.stockfarmer.core.rules.select
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

internal class MovingOriginsTest {
    private val now = Instant.ofEpochSecond(100_000)
    private val tile = GridPoint(10, 10)
    private val engine = GameEngine()

    @Test
    fun `nudging a carried building hides its original footprint until cancellation`() {
        engine.state.putBuilding(tile, Building.starter.id)
        engine.beginMoving(engine.state.placedBuildings.first())
        engine.nudgePlacement(1, 2)
        assertEquals(MovingOrigins(building = tile), MovingOrigins.of(engine))
        engine.cancelPlacement()
        assertEquals(MovingOrigins.NONE, MovingOrigins.of(engine))
        assertEquals(Building.starter.id, engine.state.buildings[tile])
    }

    @Test
    fun `new placement hides no existing building`() {
        engine.state.putBuilding(tile, Building.starter.id)
        engine.select(Building.starter)
        engine.beginPlacing(GridPoint(20, 20))
        assertEquals(MovingOrigins.NONE, MovingOrigins.of(engine))
    }

    @Test
    fun `switching from a carried decoration to a tree clears the old hidden origin`() {
        val other = GridPoint(20, 20)
        engine.state.putDecoration(tile, PlacedDecoration(Decoration.all.first { it.id == "well" }.id))
        engine.state.putTree(other, PlacedTree(Tree.starter, TreeColor.green, now, false))
        engine.beginMovingDecoration(tile)
        assertEquals(MovingOrigins(decoration = tile), MovingOrigins.of(engine))
        engine.beginMovingTree(other)
        assertEquals(MovingOrigins(tree = other), MovingOrigins.of(engine))
        engine.nudgePlacement(1, 1)
        assertEquals(MovingOrigins(tree = other), MovingOrigins.of(engine))
        engine.cancelPlacement()
        assertEquals(MovingOrigins.NONE, MovingOrigins.of(engine))
        assertEquals(1, engine.state.decorations.size)
        assertEquals(1, engine.state.trees.size)
    }

    @Test
    fun `naturally generated trees and empty tiles never become hidden moving objects`() {
        engine.state.putTree(tile, PlacedTree(Tree.starter, TreeColor.green, now, true))
        engine.beginMovingTree(tile)
        assertEquals(MovingOrigins.NONE, MovingOrigins.of(engine))
        engine.beginMovingDecoration(GridPoint(0, 0))
        engine.beginMovingFruitTree(GridPoint(0, 0))
        assertEquals(MovingOrigins.NONE, MovingOrigins.of(engine))
    }

    @Test
    fun `nothing is left out while nothing is moving`() {
        engine.state.putBuilding(tile, Building.named("chickenCoop")!!.id)
        assertEquals(MovingOrigins.NONE, MovingOrigins.of(engine))
    }

    @Test
    fun `a building being moved is left out of its old spot`() {
        engine.state.putBuilding(tile, Building.named("chickenCoop")!!.id)
        engine.beginMoving(engine.state.placedBuildings.first())
        assertEquals(MovingOrigins(building = tile), MovingOrigins.of(engine))
    }

    @Test
    fun `a fruit tree being moved is left out of its old spot`() {
        engine.state.plots[tile] = Plot(Crop.named("apple")!!.id, now, origin = tile)
        engine.beginMovingFruitTree(tile)
        assertEquals(MovingOrigins(fruitTree = tile), MovingOrigins.of(engine))
    }

    @Test
    fun `a decoration being moved is left out of its old spot`() {
        engine.state.putDecoration(tile, PlacedDecoration(Decoration.all.first { it.id == "well" }.id))
        engine.beginMovingDecoration(tile)
        assertEquals(MovingOrigins(decoration = tile), MovingOrigins.of(engine))
    }

    @Test
    fun `a tree being moved is left out of its old spot`() {
        engine.state.putTree(tile, PlacedTree(Tree.starter, TreeColor.green, now, false))
        engine.beginMovingTree(tile)
        assertEquals(MovingOrigins(tree = tile), MovingOrigins.of(engine))
    }
}
