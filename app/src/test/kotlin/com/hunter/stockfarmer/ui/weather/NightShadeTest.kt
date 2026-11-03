package com.hunter.stockfarmer.ui.weather

import com.hunter.stockfarmer.core.rules.DayNightCycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NightShadeTest {
    private fun tint(minute: Double): List<Double> {
        val darkness = DayNightCycle.darkness(minute)
        return listOf(NightShade.DEEPEST_RED, NightShade.DEEPEST_GREEN, NightShade.DEEPEST_BLUE)
            .map { NightShade.channel(it, darkness) }
    }

    private fun assertTint(
        expected: List<Double>,
        actual: List<Double>,
    ) = expected.zip(actual).forEach { (want, got) -> assertEquals(want, got, 1e-9) }

    @Test
    fun `midday multiplies by white and changes nothing`() {
        assertTint(listOf(1.0, 1.0, 1.0), tint(12 * 60.0))
    }

    @Test
    fun `full night multiplies by the deep blue of iOS`() {
        assertTint(listOf(0.42, 0.49, 0.72), tint(22 * 60.0))
        assertTint(listOf(0.42, 0.49, 0.72), tint(2 * 60.0))
    }

    @Test
    fun `halfway through dusk the tint is halfway to the deep blue`() {
        assertTint(listOf(0.71, 0.745, 0.86), tint(20.5 * 60))
    }

    @Test
    fun `at five in the morning it is half dark`() {
        assertTint(listOf(0.71, 0.745, 0.86), tint(5 * 60.0))
    }
}
