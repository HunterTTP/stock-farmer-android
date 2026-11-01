package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.farm.effects.FireworkArea
import com.hunter.stockfarmer.game.ViewSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FarmFireworksTest {
    @Test fun `fireworks launch across the bottom sixty percent of the panned and zoomed view`() {
        val view = ViewSnapshot(2000f, 3000f, 2f, 800, 600, 0.5f)
        assertEquals(FireworkArea(1200.0, 2400.0, 1600.0, 720.0), view.fireworkGround())
        assertEquals(
            FireworkArea(1800.0, 2850.0, 400.0, 180.0),
            view.copy(worldPerPixel = 0.5f).fireworkGround(),
        )
    }
}
