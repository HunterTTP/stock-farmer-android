package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.firstRejection
import com.hunter.stockfarmer.core.rules.isPlacing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FarmHoldTest {
    private val now = Instant.ofEpochSecond(100_000)
    private val tile = GridPoint(10, 10)
    private val engine = GameEngine()
    private val session = GameSession(engine)

    @Test
    fun `holding a building begins moving it`() {
        val coop = Building.named("chickenCoop")!!
        engine.state.putBuilding(tile, coop.id)
        assertFalse(engine.isPlacing)
        val events = session.hold(tile, now)
        assertTrue(events.isEmpty())
        assertTrue(engine.isPlacing)
    }

    @Test
    fun `holding a fruit tree begins moving it`() {
        val apple = Crop.named("apple")!!
        engine.state.plots[tile] = Plot(apple.id, now, origin = tile)
        val events = session.hold(tile, now)
        assertTrue(events.isEmpty())
        assertTrue(engine.isPlacing)
    }

    @Test
    fun `holding a decoration begins moving it`() {
        val well = Decoration.all.first { it.id == "well" }
        engine.state.putDecoration(tile, PlacedDecoration(well.id))
        val events = session.hold(tile, now)
        assertTrue(events.isEmpty())
        assertTrue(engine.isPlacing)
    }

    @Test
    fun `holding a naturally generated tree rejects with use the axe`() {
        engine.state.putTree(tile, PlacedTree(Tree.starter, TreeColor.green, now, isNaturallyGenerated = true))
        val events = session.hold(tile, now)
        assertEquals(Rejection.UseTheAxe, events.firstRejection)
        assertFalse(engine.isPlacing)
    }

    @Test
    fun `holding a planted tree begins moving it`() {
        engine.state.putTree(tile, PlacedTree(Tree.starter, TreeColor.green, now, isNaturallyGenerated = false))
        val events = session.hold(tile, now)
        assertTrue(events.isEmpty())
        assertTrue(engine.isPlacing)
    }

    @Test
    fun `holding a rock rejects with use the pickaxe`() {
        engine.state.rocks[tile] = RockSize.entries.first()
        val events = session.hold(tile, now)
        assertEquals(Rejection.UseThePickaxe, events.firstRejection)
        assertFalse(engine.isPlacing)
    }

    @Test
    fun `holding crops asks to destroy them with confirmation`() {
        val wheat = Crop.starter
        engine.state.plots[tile] = Plot(wheat.id, now)
        val events = session.hold(tile, now)
        assertTrue(events.isEmpty())
        val question = session.confirmations.value
        assertNotNull(question)
        assertEquals("Destroy 1 crop?", question!!.title)
        assertTrue(question.message.contains("Grow time remaining"))
        question.action()
        assertNull(engine.state.plots[tile])
    }

    @Test
    fun `holding fertilizer asks to destroy it with confirmation`() {
        engine.state.fertilized.add(tile)
        val events = session.hold(tile, now)
        assertTrue(events.isEmpty())
        val question = session.confirmations.value
        assertNotNull(question)
        assertEquals("Destroy fertilizer?", question!!.title)
        question.action()
        assertFalse(tile in engine.state.fertilized)
    }

    @Test
    fun `holding while already placing does nothing`() {
        val coop = Building.named("chickenCoop")!!
        engine.state.putBuilding(tile, coop.id)
        session.hold(tile, now)
        assertTrue(engine.isPlacing)
        val secondEvents = session.hold(GridPoint(5, 5), now)
        assertTrue(secondEvents.isEmpty())
        assertNull(session.confirmations.value)
    }
}
