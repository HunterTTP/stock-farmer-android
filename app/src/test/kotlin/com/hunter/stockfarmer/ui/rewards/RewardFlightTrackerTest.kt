package com.hunter.stockfarmer.ui.rewards

import androidx.compose.ui.graphics.Color
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.ui.theme.Theme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RewardFlightTrackerTest {
    private val tile = GridPoint(2, 3)
    private val position: (GridPoint) -> RewardPoint? = { RewardPoint(it.col * 100.0, it.row * 100.0) }

    @Test fun `spending is one flight from the mean of visible paid tiles`() {
        val tracker = RewardFlightTracker()
        tracker.launch(
            listOf(
                GameEvent.Planted(tile, 2.0),
                GameEvent.Built(GridPoint(4, 5), 4.0),
                GameEvent.Fertilized(tile, 0.0),
            ),
            0.0,
            position,
        )
        assertEquals(RewardKind.Spend(6.0), tracker.flights.single().kind)
        assertEquals(RewardPoint(400.0, 300.0), tracker.flights.single().start)
        assertEquals(100.0, tracker.displayedMoney(94.0))
        tracker.tick(1.0)
        assertEquals(94.0, tracker.displayedMoney(94.0))
        assertEquals(1, tracker.moneyPulse)
    }

    @Test fun `harvest keeps sold and stored sources separate and holds totals until final landing`() {
        val tracker = RewardFlightTracker()
        tracker.launch(
            listOf(
                GameEvent.Sold(tile, "beetroot", 3.0, 2),
                GameEvent.Sold(tile, "beetroot", 4.0, 3),
                GameEvent.Stored(tile, "beetroot", 1),
            ),
            0.0,
            position,
        )
        assertEquals(5, tracker.flights.size)
        assertEquals(93.0, tracker.displayedMoney(100.0))
        assertEquals(4, tracker.displayedXP(10))
        tracker.tick(0.8)
        assertEquals(listOf(2, 1), tracker.tally!!.items.map { it.count })
        assertEquals(listOf(7.0, 0.0), tracker.tally!!.items.map { it.earned })
        assertEquals(listOf(5, 1), tracker.tally!!.items.map { it.xp })
        tracker.tick(1.8)
        assertNull(tracker.tally)
        assertTrue(tracker.flights.all { it.route is RewardRoute.FromTally })
        assertEquals(4, tracker.displayedXP(10))
        tracker.tick(3.0)
        assertEquals(100.0, tracker.displayedMoney(100.0))
        assertEquals(10, tracker.displayedXP(10))
        assertEquals(1, tracker.storagePulse)
        assertEquals(2, tracker.xpPulse)
    }

    @Test fun `flight cap never loses rapid harvest counts or held earnings`() {
        val tracker = RewardFlightTracker()
        repeat(30) { tracker.launch(listOf(GameEvent.Sold(tile, "beetroot", 2.0, 1)), 0.0, position) }
        assertEquals(12, tracker.flights.size)
        tracker.tick(0.8)
        assertEquals(
            30,
            tracker.tally!!
                .items
                .single()
                .count,
        )
        assertEquals(
            60.0,
            tracker.tally!!
                .items
                .single()
                .earned,
        )
        tracker.tick(10.0)
        assertEquals(100.0, tracker.displayedMoney(100.0))
        assertEquals(50, tracker.displayedXP(50))
        assertTrue(tracker.flights.isEmpty())
    }

    @Test fun `tractor quantities count but menu cargo and unknown items do not fly`() {
        val tracker = RewardFlightTracker()
        tracker.launch(
            listOf(
                GameEvent.TractorCargoStored("beetroot", 12, tile, 7),
                GameEvent.TractorCargoSold("beetroot", 6, 20.0, tile, 3),
                GameEvent.TractorCargoStored("beetroot", 10),
                GameEvent.Stored(tile, "missing", 100),
            ),
            0.0,
            position,
        )
        tracker.tick(0.8)
        assertEquals(listOf(12, 6), tracker.tally!!.items.map { it.count })
        assertEquals(10, tracker.displayedXP(20))
    }

    @Test fun `quest waits for banner and clearing cancels every pending landing`() {
        val tracker = RewardFlightTracker()
        tracker.launch(listOf(GameEvent.QuestCompleted(12, "Beets")), 0.0, position)
        assertTrue(tracker.flights.single().startsAtBanner)
        assertEquals(0.8, tracker.flights.single().delay)
        tracker.tick(1.7)
        assertEquals(8, tracker.displayedXP(20))
        tracker.clear()
        tracker.tick(10.0)
        assertEquals(20, tracker.displayedXP(20))
        assertEquals(0, tracker.xpPulse)
        assertNull(tracker.tally)
    }

    @Test fun `unmapped events leave HUD totals alone`() {
        val tracker = RewardFlightTracker()
        tracker.launch(listOf(GameEvent.Sold(tile, "beetroot", 4.0, 2), GameEvent.Planted(tile, 3.0)), 0.0) { null }
        assertTrue(tracker.flights.isEmpty())
        assertEquals(10.0, tracker.displayedMoney(10.0))
        assertEquals(10, tracker.displayedXP(10))
    }

    @Test fun `reward kinds map to HUD theme colors`() {
        assertEquals(Theme.spend, RewardKind.Spend(5.0).color)
        assertEquals(Theme.money, RewardKind.Earn(10.0).color)
        assertEquals(Theme.xp, RewardKind.XP(25).color)
        assertEquals(Color.White, RewardKind.Item(StorageItem.Egg, 2, false).color)
    }
}
