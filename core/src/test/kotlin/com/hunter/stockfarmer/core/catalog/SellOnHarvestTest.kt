package com.hunter.stockfarmer.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SellOnHarvestTest {
    @Test fun `theAxeAndPickaxeCannotSellSoTheyStoreEvenIfOldSaveSaidSell`() {
        // The engine and save migration assertions await GameEngine.
        assertEquals(
            listOf(HarvestTool.axe, HarvestTool.pickaxe, HarvestTool.rod),
            HarvestTool.entries.filter { !it.canSell },
        )
    }
}
