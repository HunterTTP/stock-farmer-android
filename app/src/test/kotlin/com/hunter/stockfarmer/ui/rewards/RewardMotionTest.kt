package com.hunter.stockfarmer.ui.rewards

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.game.FarmView
import com.hunter.stockfarmer.game.ViewSnapshot
import com.hunter.stockfarmer.game.isOnScreen
import com.hunter.stockfarmer.game.screenPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RewardMotionTest {
    @Test fun `projection reverses tileAt at different camera positions and zooms`() {
        for (worldPerPixel in listOf(0.5f, 1f, 4f)) {
            val view = FarmView()
            val camera = ViewSnapshot(500f, 2500f, worldPerPixel, 900, 600, 0.2f)
            view.snapshot = camera
            for (tile in listOf(GridPoint(2, 3), GridPoint(15, 19), GridPoint(31, 0))) {
                val screen = camera.screenPosition(tile, 100f, 40)!!
                assertEquals(tile, view.tileAt(screen.x, screen.y, 100f, 40))
            }
            val center = camera.screenPosition(GridPoint(14, 4), 100f, 40)!!
            assertTrue(camera.isOnScreen(center))
            assertFalse(camera.isOnScreen(camera.screenPosition(GridPoint(0, 39), 100f, 40)!!))
        }
        assertNull(ViewSnapshot(0f, 0f, 0f, 100, 100, 0f).screenPosition(GridPoint(0, 0), 100f, 40))
    }

    @Test fun `direct flight waits rises then lands at the measured target`() {
        val flight = RewardFlight(0, RewardKind.Earn(3.0), RewardPoint(300.0, 500.0), 0.0, delay = 0.1)
        val target = RewardPoint(30.0, 40.0)
        val tally = RewardPoint(200.0, 185.0)
        assertEquals(0f, flight.pose(0.0, tally, tally, target).alpha)
        val risen = flight.pose(0.5, tally, tally, target)
        assertEquals(464.0, risen.position.y, 0.001)
        assertEquals(1f, risen.alpha, 0.001f)
        val landed = flight.pose(flight.landingAt, tally, tally, target)
        assertEquals(target.x, landed.position.x, 0.001)
        assertEquals(target.y, landed.position.y, 0.001)
        assertEquals(0f, landed.alpha, 0.001f)
    }

    @Test fun `tally departure starts from its own row and quest starts at the banner`() {
        val tally = RewardPoint(200.0, 185.0)
        val offset = RewardPoint(-92.0, 40.0)
        val flight =
            RewardFlight(0, RewardKind.XP(2), RewardPoint(0.0, 0.0), 0.0, route = RewardRoute.FromTally(offset))
        val pose = flight.pose(0.0, tally, RewardPoint(100.0, 300.0), tally)
        assertEquals(108.0, pose.position.x, 0.001)
        assertEquals(225.0, pose.position.y, 0.001)
        assertEquals(1f, pose.alpha, 0.001f)
        val quest = flight.copy(route = RewardRoute.Direct, startsAtBanner = true, delay = 0.8)
        assertEquals(RewardPoint(100.0, 300.0), quest.pose(0.0, tally, RewardPoint(100.0, 300.0), tally).position)
    }
}
