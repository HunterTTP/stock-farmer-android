package com.hunter.stockfarmer.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RockTest {
    @Test fun `biggerRocksGiveMoreStones`() {
        assertEquals(listOf(2, 4, 6), RockSize.stoneSizes.map { it.stones })
        assertEquals(
            listOf("Rock_Small_48x48", "Rock_Medium_48x48", "Rock_Big_48x48"),
            RockSize.stoneSizes.map {
                it.assetName
            },
        )
    }

    @Test fun `plainRocksTakeTwoPassesForEveryStoneTheyGive`() {
        assertEquals(listOf(4, 8, 12), RockSize.stoneSizes.map { it.hitsToBreak })
    }

    @Test fun `veinsStillTakeOnePassForEveryStone`() {
        assertEquals(RockSize.oreSizes.map { it.stones }, RockSize.oreSizes.map { it.hitsToBreak })
    }

    @Test fun `mediumAndBigRocksAreDrawnLargerButAllStandOnOneTile`() {
        assertEquals(listOf(1.0, 1.25, 1.5625), RockSize.stoneSizes.map { it.spriteScale })
        assertTrue(RockSize.oreSizes.all { it.spriteScale == 1.0 })
    }
}
