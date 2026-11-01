package com.hunter.stockfarmer.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class FishTest {
    @Test fun `thereAreFortyTwoKindsOfFishEachWithAPictureNameAndPrice`() {
        assertEquals(42, FishKind.all.size)
        assertEquals(
            42,
            FishKind.all
                .map { it.id }
                .toSet()
                .size,
        )
        assertTrue(FishKind.all.all { it.name.isNotEmpty() && it.value > 0 && it.assetName == "fish_fishing-${it.id}" })
        assertEquals("Carp", FishKind.named("carp")?.name)
        assertNull(FishKind.named("nothing"))
    }

    @Test fun `heavierFishAreDrawnBiggerAnAverageOneAtNormalSize`() {
        // The default PondFish size assertion awaits PondFish.
        for (kind in FishKind.all) {
            val range = kind.weightRange
            val middle = (range.start + range.endInclusive) / 2
            assertTrue(abs(kind.sizeFactor(middle) - 1) < 0.000_001)
            assertEquals(FishKind.lightestSizeFactor, kind.sizeFactor(range.start))
            assertEquals(FishKind.heaviestSizeFactor, kind.sizeFactor(range.endInclusive))
            assertTrue(kind.sizeFactor(range.start) < kind.sizeFactor(middle))
            assertTrue(kind.sizeFactor(middle) < kind.sizeFactor(range.endInclusive))
        }
    }
}
