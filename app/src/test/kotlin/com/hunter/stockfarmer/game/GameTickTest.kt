package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.currentLesson
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class GameTickTest {
    private val planted = Instant.ofEpochSecond(100)
    private val ripe = planted.advancedBy(30.0)
    private val tile = GridPoint(24, 24)

    @Test
    fun `tick advances the lesson when planted wheat becomes ripe`() {
        val engine = GameEngine()
        engine.tap(tile, planted)
        assertEquals(Lesson.grow, engine.currentLesson)

        val session = GameSession(engine)
        val tick = GameTick(session)

        // Initial tick establishes timing baseline
        tick.tick(planted)
        assertEquals(Lesson.grow, engine.currentLesson)

        // Tick before wheat has ripened stays on waiting
        tick.tick(planted.advancedBy(1.0))
        assertEquals(Lesson.grow, engine.currentLesson)

        // Tick after wheat ripens advances to picking harvest tool
        tick.tick(ripe)
        assertEquals(Lesson.pickHarvest, engine.currentLesson)
    }

    @Test
    fun `tick advances straight to harvesting if scythe was already selected when wheat ripens`() {
        val engine = GameEngine()
        engine.tap(tile, planted)
        engine.setMode(PlayMode.harvest)
        engine.select(HarvestTool.scythe)
        assertEquals(Lesson.grow, engine.currentLesson)

        val session = GameSession(engine)
        val tick = GameTick(session)

        tick.tick(planted)
        tick.tick(ripe)
        assertEquals(Lesson.harvest, engine.currentLesson)
    }
}
