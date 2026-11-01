package com.hunter.stockfarmer.core.catalog

data class HomeUpgrade(
    val buildingID: String,
    val level: Int,
    val unlockLevel: Int,
    val cost: Double,
) {
    val careerID: String get() = "$buildingID-$level"

    companion object {
        val capacities = listOf(4, 7, 10)
        val coopCapacities = listOf(2, 4, 6)
        val maxLevel = capacities.size

        fun capacity(
            buildingID: String,
            atLevel: Int,
        ): Int =
            (
                if (buildingID ==
                    "chickenCoop"
                ) {
                    coopCapacities
                } else {
                    capacities
                }
            )[atLevel.coerceIn(1, maxLevel) - 1]

        val all =
            listOf(
                HomeUpgrade("chickenCoop", 2, 7, 770.0),
                HomeUpgrade("rabbitHutch", 2, 9, 950.0),
                HomeUpgrade("barn1", 2, 11, 2_200.0),
                HomeUpgrade("chickenCoop", 3, 12, 1_700.0),
                HomeUpgrade("goatBarn", 2, 13, 2_400.0),
                HomeUpgrade("rabbitHutch", 3, 14, 1_700.0),
                HomeUpgrade("pigBarn", 2, 15, 1_300.0),
                HomeUpgrade("barn1", 3, 16, 3_700.0),
                HomeUpgrade("stable1", 2, 17, 1_200.0),
                HomeUpgrade("goatBarn", 3, 18, 3_600.0),
                HomeUpgrade("pigBarn", 3, 20, 2_000.0),
                HomeUpgrade("barn2", 2, 21, 6_300.0),
                HomeUpgrade("stable1", 3, 22, 1_800.0),
                HomeUpgrade("barn2", 3, 24, 7_100.0),
            )
        val buildingIDs = listOf("chickenCoop", "rabbitHutch", "barn1", "goatBarn", "pigBarn", "stable1", "barn2")

        fun upgrade(
            buildingID: String,
            toLevel: Int,
        ): HomeUpgrade? =
            all.firstOrNull {
                it.buildingID == buildingID &&
                    it.level == toLevel
            }
    }
}
