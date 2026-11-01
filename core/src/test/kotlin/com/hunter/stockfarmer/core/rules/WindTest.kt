package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class WindTest {
    private val start = Instant.ofEpochSecond(60_000_000)

    @Test
    fun `the wind keeps its way for five real minutes`() {
        // A real second is a minute on the farm's clock, so five real minutes are three hundred of its minutes.
        for (spell in 0 until 50) {
            val begin = start.advancedBy(spell * 300.0)
            val way = Wind.pushesRight(begin, 0.0)
            var second = 0.0
            while (second < 299.9) {
                assertEquals(way, Wind.pushesRight(begin.advancedBy(second), 0.0))
                second += 7.3
            }
        }
    }

    @Test
    fun `the wind changes way now and then but not every five minutes`() {
        val ways = (0 until 400).map { Wind.pushesRight(start.advancedBy(it * 300.0), 0.0) }
        val changes = ways.zipWithNext().count { (a, b) -> a != b }
        assertTrue(changes in 101..299)
        assertTrue(ways.count { it } > 120 && ways.count { !it } > 120)
    }

    @Test
    fun `the way follows the farms clock so setting the time changes it`() {
        assertEquals(Wind.pushesRight(start, 0.0), Wind.pushesRight(start, 0.0))
        val ways = (0 until 20).map { Wind.pushesRight(start, it * 300.0) }.toSet()
        assertEquals(setOf(true, false), ways)
    }
}
