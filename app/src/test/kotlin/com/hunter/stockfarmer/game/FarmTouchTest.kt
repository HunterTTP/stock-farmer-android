package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.firstRejection
import com.hunter.stockfarmer.core.rules.select
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FarmTouchTest {
    private val now = Instant.ofEpochSecond(60_000_000)
    private val tile = GridPoint(20, 20)
    private val engine = GameEngine()
    private val session = GameSession(engine)

    @Test
    fun `rock tap asks for the pickaxe before using the held tool`() {
        engine.state.rocks[tile] = RockSize.entries.first()
        engine.state.mode = PlayMode.plant
        assertEquals(Rejection.UseThePickaxe, session.touch(tile, now).firstRejection)
        assertTrue(engine.state.plots.isEmpty())
        assertEquals(
            listOf(tile),
            session.sceneFeedback
                .drainFlashes()
                .single()
                .tiles,
        )
    }

    @Test
    fun `fence tap asks before refunding and the axe removes it directly`() {
        engine.state.fences[tile] = FenceColor.starter.id
        session.touch(tile, now)
        assertEquals("Sell fence?", session.confirmations.value?.title)
        assertTrue(tile in engine.state.fences)
        session.confirmations.value!!.action()
        assertNull(engine.state.fences[tile])
        assertEquals(FenceColor.price, engine.state.money)
        engine.state.fences[tile] = FenceColor.starter.id
        engine.select(HarvestTool.axe)
        session.confirmations.value = null
        session.touch(tile, now)
        assertNull(engine.state.fences[tile])
        assertNull(session.confirmations.value)
    }

    @Test
    fun `sprinkler menu includes schedule and a farm wide always on alternative`() {
        val sprinkler = Decoration.all.first { it.isSprinkler }
        engine.state.putDecoration(tile, PlacedDecoration(sprinkler.id))
        session.touch(tile, now)
        val question = session.confirmations.value!!
        assertTrue(question.message.startsWith("Sprays again in "))
        assertEquals("Turn Always On", question.alternative?.title)
        question.alternative!!.action()
        assertTrue(engine.state.sprinklersRunConstantly)
        session.touch(tile, now)
        assertEquals("Always On: every sprinkler sprays all the time", session.confirmations.value!!.message)
        assertEquals(
            "Turn Always On off",
            session.confirmations.value!!
                .alternative
                ?.title,
        )
    }

    @Test
    fun `a crop answers taps even when a different mode is selected`() {
        engine.state.plots[tile] = Plot("wheat", now)
        engine.state.mode = PlayMode.landscape
        val events = session.touch(tile, now)
        assertNotNull(events.firstRejection)
        assertEquals("wheat", engine.state.plots[tile]?.cropID)
        assertTrue(engine.state.groundCover.isEmpty())
    }

    @Test
    fun `rod taps start fishing instead of opening the fish sheet`() {
        engine.state.water[tile] = WaterStyle.deep
        engine.state.fishPonds[tile] = FishPond(PondFish("bluegill", tile, 1.2, now), now)
        engine.select(HarvestTool.rod)
        assertTrue(session.touch(tile, now).any { it is GameEvent.FishHooked })
        assertNotNull(session.casts.value)
        assertNull(session.sheets.value)
    }
}
