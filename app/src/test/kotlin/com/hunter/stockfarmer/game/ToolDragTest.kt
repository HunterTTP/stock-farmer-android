package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.select
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ToolDragTest {
    @Test
    fun `staying on a tile gives just that tile`() {
        assertEquals(listOf(GridPoint(5, 5)), ToolDrag.tilesBetween(GridPoint(5, 5), GridPoint(5, 5)))
    }

    @Test
    fun `a quick drag along a row skips no tile`() {
        assertEquals(
            listOf(GridPoint(5, 6), GridPoint(5, 7), GridPoint(5, 8)),
            ToolDrag.tilesBetween(GridPoint(5, 5), GridPoint(5, 8)),
        )
    }

    @Test
    fun `a diagonal drag steps one tile each way`() {
        assertEquals(
            listOf(GridPoint(1, 1), GridPoint(2, 2), GridPoint(3, 3)),
            ToolDrag.tilesBetween(GridPoint(0, 0), GridPoint(3, 3)),
        )
    }

    @Test
    fun `a drag ends on the tile the finger is over`() {
        assertEquals(GridPoint(2, 9), ToolDrag.tilesBetween(GridPoint(8, 1), GridPoint(2, 9)).last())
    }

    @Test
    fun `dragging bucket catches butterflies on tiles passed over`() {
        val session = GameSession()
        val drag = ToolDrag(session)
        val target = GridPoint(2, 3)
        var caught = false
        session.sceneFeedback.catchButterfly = { point, _ ->
            if (point == target) {
                caught = true
                listOf(GameEvent.Placed(point))
            } else {
                emptyList()
            }
        }
        drag.move(GridPoint(2, 1))
        drag.move(GridPoint(2, 4))
        assertTrue(caught)
        drag.end()
    }

    @Test
    fun `chopping tool hides tool preview on drag`() {
        val engine = GameEngine()
        engine.select(HarvestTool.axe)
        val session = GameSession(engine)
        val drag = ToolDrag(session)
        drag.move(GridPoint(5, 5))
        assertNull(session.view.toolPreview)
        drag.end()
    }

    @Test
    fun `resting over ripe crop with wrong tool explains missing harvest tool`() {
        val now = Instant.ofEpochSecond(100_000)
        val engine = GameEngine()
        val tile = GridPoint(3, 3)
        engine.state.plots[tile] = Plot(Crop.starter.id, now.minusSeconds(10_000))
        engine.select(HarvestTool.bucket)
        val session = GameSession(engine)
        val drag = ToolDrag(session)
        drag.move(tile, now)
        assertEquals(Rejection.UseTheScythe, drag.explain(now))
        val preview = session.view.toolPreview
        assertTrue(preview != null && !preview.canPlace)
        drag.end()
        assertNull(drag.explain(now))
    }
}
