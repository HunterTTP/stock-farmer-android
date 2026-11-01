package com.hunter.stockfarmer.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeUpgradeTest {
    @Test fun `everyHomeHasTwoUpgradesAtDifferentLevelsAndRisingPrices`() {
        // Building unlock and LevelRules assertions await those subjects.
        assertEquals(listOf(4, 7, 10), HomeUpgrade.capacities)
        assertEquals(listOf(2, 4, 6), HomeUpgrade.coopCapacities)
        assertEquals(listOf(2, 4, 6), (1..3).map { HomeUpgrade.capacity("chickenCoop", it) })
        assertEquals(listOf(4, 7, 10), (1..3).map { HomeUpgrade.capacity("barn1", it) })
        assertEquals(HomeUpgrade.buildingIDs.size * 2, HomeUpgrade.all.size)
        for (id in HomeUpgrade.buildingIDs) {
            val second = HomeUpgrade.upgrade(id, 2)!!
            val third = HomeUpgrade.upgrade(id, 3)!!
            assertTrue(third.unlockLevel > second.unlockLevel && third.cost > second.cost / 2)
        }
        assertEquals(
            HomeUpgrade.all.size,
            HomeUpgrade.all
                .map { it.unlockLevel }
                .toSet()
                .size,
        )
        assertEquals(
            HomeUpgrade.all.size,
            HomeUpgrade.all
                .map { it.careerID }
                .toSet()
                .size,
        )
    }
}
