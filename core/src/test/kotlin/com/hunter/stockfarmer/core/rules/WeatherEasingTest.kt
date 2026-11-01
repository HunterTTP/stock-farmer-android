package com.hunter.stockfarmer.core.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeatherEasingTest {
    @Test fun `the easing starts at nothing ends at everything and meets in the middle`() {
        assertEquals(0.0, Weather.eased(0.0))
        assertEquals(1.0, Weather.eased(1.0))
        assertEquals(0.5, Weather.eased(0.5))
    }

    @Test fun `the first seconds of rain are nearly silent and still`() {
        assertTrue(Weather.eased(0.05) < 0.01)
        assertTrue(Weather.eased(0.95) > 0.99)
    }

    @Test fun `the easing never falls as the rain builds`() {
        val levels = (0..100).map { Weather.eased(it * 0.01) }
        assertEquals(levels.sorted(), levels)
    }
}
