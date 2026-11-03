package com.hunter.stockfarmer.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** The indexed lookups answer exactly as the scans they replaced: the first catalog entry with the id, or null. */
class NamedLookupTest {
    private val misses = listOf("", "nope", "PINE-small", "pine", "wood ", "Wood", "dairy_empty", "barn")

    @Test
    fun `tree lookup agrees with a scan for every tree and for misses`() {
        for (tree in Tree.all) assertSame(Tree.all.first { it.id == tree.id }, Tree.named(tree.id))
        for (id in misses) assertEquals(Tree.all.firstOrNull { it.id == id }, Tree.named(id))
        assertNull(Tree.named("nope"))
        assertEquals(
            Tree.all.size,
            Tree.all
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun `building lookup agrees with a scan for every building and for misses`() {
        for (building in Building.all) {
            assertSame(
                Building.all.first { it.id == building.id },
                Building.named(building.id),
            )
        }
        for (id in misses) {
            val scanned = Building.all.firstOrNull { it.id == id }
            assertEquals(scanned, Building.named(id))
        }
        assertEquals(
            Building.all.size,
            Building.all
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun `the upgraded buildings are still found by their own ids`() {
        assertSame(Building.upgradedMarketStand, Building.named(Building.upgradedMarketStand.id))
        assertSame(Building.upgradedHouse, Building.named(Building.upgradedHouse.id))
    }

    @Test
    fun `fence color lookup agrees with a scan for every color and for misses`() {
        for (color in FenceColor.all) assertSame(FenceColor.all.first { it.id == color.id }, FenceColor.named(color.id))
        for (id in misses) assertEquals(FenceColor.all.firstOrNull { it.id == id }, FenceColor.named(id))
        assertEquals(
            FenceColor.all.size,
            FenceColor.all
                .map { it.id }
                .toSet()
                .size,
        )
    }
}
