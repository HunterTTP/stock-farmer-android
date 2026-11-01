package com.hunter.stockfarmer.core.feedback

import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnimalChatterTest {
    private fun run(
        species: List<String>,
        seconds: Int,
        seed: Long = 1,
    ): List<Pair<Double, String>> {
        var chatter = AnimalChatter<String>(60.0, 5.0)
        val generator = SeededRandom(seed)
        return (0 until seconds).mapNotNull { second ->
            val (next, speaker) = chatter.nextSpeaker(second.toDouble(), species, generator)
            chatter = next
            speaker?.let { second.toDouble() to it }
        }
    }

    @Test fun `each species speaks once in every stretch`() {
        assertTrue(run(listOf("cow"), 600).size in 8..10)
    }

    @Test fun `two species never speak within the gap of each other`() {
        repeat(50) { seed ->
            val spoke = run(listOf("cow", "pig", "hen", "dog"), 600, seed.toLong())
            spoke.zipWithNext().forEach { (earlier, later) -> assertTrue(later.first - earlier.first >= 5.0) }
        }
    }

    @Test fun `a species not asked about does not speak`() {
        assertTrue(run(emptyList(), 300).isEmpty())
    }

    @Test fun `every species gets its turn even when they are due together`() {
        assertEquals(setOf("cow", "pig", "hen"), run(listOf("cow", "pig", "hen"), 6000).map { it.second }.toSet())
    }
}
