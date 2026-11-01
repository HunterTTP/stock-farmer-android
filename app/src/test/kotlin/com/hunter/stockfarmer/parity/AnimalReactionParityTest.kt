package com.hunter.stockfarmer.parity

import com.hunter.stockfarmer.farm.animals.AnimalHearts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** AnimalNode.swift: replacing the keyed hearts action leaves already emitted child nodes alive. */
internal class AnimalReactionParityTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `each heart holds opacity for half a second then fades and is removed after a second`() {
        for (index in 0..2) {
            val hearts = AnimalHearts()
            hearts.pet(start)
            val birth = 600L + index * 800
            val emitted = hearts.hearts(start.plusMillis(birth)).last()
            assertEquals(0f, emitted.rise, 0.0001f)
            assertEquals(1f, emitted.alpha)
            val halfway = hearts.hearts(start.plusMillis(birth + 500)).last()
            assertEquals(20f, halfway.rise, 0.0001f)
            assertEquals(1f, halfway.alpha)
            val faded = hearts.hearts(start.plusMillis(birth + 750)).last()
            assertEquals(30f, faded.rise, 0.0001f)
            assertEquals(0.5f, faded.alpha, 0.0001f)
        }
        val hearts = AnimalHearts()
        hearts.pet(start)
        assertTrue(hearts.hearts(start.plusMillis(3200)).isEmpty())
    }

    @Test
    fun `petting again preserves emitted hearts but replaces the pending emissions`() {
        val hearts = AnimalHearts()
        hearts.pet(start)
        hearts.pet(start.plusMillis(1000))
        val first = hearts.hearts(start.plusMillis(1100)).single()
        assertEquals(20f, first.rise, 0.0001f)
        assertEquals(1f, first.alpha)
        assertEquals(1, hearts.hearts(start.plusMillis(1400)).size)
        val replacement = hearts.hearts(start.plusMillis(1600)).single()
        assertEquals(0f, replacement.rise, 0.0001f)
        assertEquals(2, hearts.hearts(start.plusMillis(2400)).size)
        assertTrue(hearts.hearts(start.plusMillis(4200)).isEmpty())
    }

    @Test
    fun `petting before the first emission restarts the six hundred millisecond wait`() {
        val hearts = AnimalHearts()
        hearts.pet(start)
        hearts.pet(start.plusMillis(500))
        assertTrue(hearts.hearts(start.plusMillis(1099)).isEmpty())
        assertEquals(1, hearts.hearts(start.plusMillis(1100)).size)
    }

    @Test
    fun `repeated petting retains both overlapping hearts and does not resurrect expired ones`() {
        val hearts = AnimalHearts()
        hearts.pet(start)
        hearts.pet(start.plusMillis(1500))
        assertEquals(2, hearts.hearts(start.plusMillis(1500)).size)
        hearts.pet(start.plusMillis(1550))
        assertEquals(2, hearts.hearts(start.plusMillis(1550)).size)
        assertEquals(1, hearts.hearts(start.plusMillis(2100)).size)
        assertEquals(2, hearts.hearts(start.plusMillis(2150)).size)
        hearts.pet(start.plusSeconds(10))
        assertTrue(hearts.hearts(start.plusSeconds(10)).isEmpty())
    }
}
