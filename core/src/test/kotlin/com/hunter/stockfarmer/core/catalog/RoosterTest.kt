package com.hunter.stockfarmer.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoosterTest {
    @Test fun `optionalRoostersHaveNewIDsAndPreserveHistoricalHenMigration`() {
        // The Coop migration assertions are blocked until Coop is ported.
        assertEquals(7, ChickenVariant.all.size)
        assertTrue(ChickenVariant.all.none { it.isRooster })
        assertEquals(7, ChickenVariant.roosters.size)
        for (rooster in ChickenVariant.roosters) {
            assertTrue(rooster.isRooster && rooster.id.startsWith("cock-"))
            assertEquals(rooster, ChickenVariant.named(rooster.id))
        }
        assertEquals(ChickenVariant.named("hen-golden"), ChickenVariant.named("rooster-golden"))
    }
}
