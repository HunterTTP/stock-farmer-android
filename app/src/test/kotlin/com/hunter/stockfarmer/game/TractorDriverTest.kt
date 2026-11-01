package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TractorDriverTest {
    private val now = Instant.ofEpochSecond(50_000)
    private val driver = TractorDriver()
    private val engine = GameEngine()

    @Test
    fun `inactive or save blocked tractor does not advance`() {
        val resultInactive = driver.advance(engine, 0.05, active = false, saveBlocked = false, now)
        assertTrue(resultInactive.isEmpty())
        val resultBlocked = driver.advance(engine, 0.05, active = true, saveBlocked = true, now)
        assertTrue(resultBlocked.isEmpty())
    }

    @Test
    fun `non rejected events pass through immediately`() {
        val tile = GridPoint(4, 4)
        val event = GameEvent.TractorGathered(tile)
        val ready = driver.present(listOf(event), now)
        assertEquals(listOf(event), ready)
    }

    @Test
    fun `rejections are paced with 2 point 3 second delay`() {
        val rej1 = GameEvent.Rejected(Rejection.TractorBusy)
        val rej2 = GameEvent.Rejected(Rejection.TractorCannotReachCrops)
        val first = driver.present(listOf(rej1, rej2), now)
        assertEquals(listOf(rej1), first)

        // Advancing by only 1 second should not emit the second rejection
        val tooEarly = driver.present(emptyList(), now.plusSeconds(1))
        assertTrue(tooEarly.isEmpty())

        // Advancing after 2.3 seconds should emit the second rejection
        val ready = driver.present(emptyList(), now.plusMillis(2300))
        assertEquals(listOf(rej2), ready)
    }

    @Test
    fun `clear resets pending notices and delay`() {
        driver.present(
            listOf(GameEvent.Rejected(Rejection.TractorBusy), GameEvent.Rejected(Rejection.TractorCannotReachCrops)),
            now,
        )
        driver.clear()
        val afterClear = driver.present(listOf(GameEvent.Rejected(Rejection.TractorNeedsRipeCrops)), now)
        assertEquals(listOf(GameEvent.Rejected(Rejection.TractorNeedsRipeCrops)), afterClear)
    }
}
