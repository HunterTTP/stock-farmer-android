package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RodFishingTest {
    @Test fun `everyKindHasASensibleWeightRange`() {
        for (kind in FishKind.all) {
            assertTrue(kind.weightRange.start > 0 && kind.weightRange.start < kind.weightRange.endInclusive, kind.id)
        }
        assertTrue(
            FishKind.named("bluegill")!!.weightRange.endInclusive < FishKind.named("bluemarlin")!!.weightRange.start,
        )
    }

    @Test fun `seeded weights vary and most are middling`() {
        val generator = SeededRandom(90)
        val carp = FishKind.named("carp")!!
        val weights = List(400) { carp.randomWeight(generator) }
        val heaviness = weights.map(carp::heaviness)
        assertTrue(weights.toSet().size > 50)
        assertTrue(heaviness.count { it in 0.25..0.75 } > heaviness.size / 2)
        assertTrue(heaviness.any { it < 0.25 } && heaviness.any { it > 0.75 })
    }
}
