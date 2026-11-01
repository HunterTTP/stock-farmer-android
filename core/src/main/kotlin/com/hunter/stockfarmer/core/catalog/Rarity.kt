@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.catalog

/** The single scale used by fish and butterflies, including their shared display colors. */
enum class Rarity(
    val color: Color,
    val fishUnlockLevel: Int,
    val fishPrice: Double,
    val xpMultiplier: Int,
) {
    common(Color(1.0, 1.0, 1.0), 1, 12.0, 1),
    uncommon(Color(0.4, 1.0, 0.5), 10, 36.0, 2),
    rare(Color(0.2, 0.5, 1.0), 10, 110.0, 4),
    epic(Color(0.75, 0.45, 1.0), 15, 330.0, 7),
    legendary(Color(1.0, 0.45, 0.0), 20, 1000.0, 10),
    ;

    data class Color(
        val red: Double,
        val green: Double,
        val blue: Double,
    )

    val rawValue: String get() = name
    val displayName: String get() = name.replaceFirstChar { it.uppercaseChar() }

    fun spawnRate(atLevel: Int): Double =
        spawnRates[(atLevel.coerceAtLeast(0) / spawnRateStep).coerceAtMost(spawnRates.lastIndex)][ordinal]

    companion object {
        val spawnRateStep = 5
        private val spawnRates =
            listOf(
                listOf(70.0, 22.0, 6.0, 2.0, 0.5),
                listOf(66.0, 24.0, 7.0, 2.5, 0.5),
                listOf(58.0, 26.0, 11.0, 4.0, 1.0),
                listOf(46.0, 26.0, 16.0, 9.0, 3.0),
                listOf(34.0, 25.0, 20.0, 15.0, 6.0),
                listOf(24.0, 22.0, 22.0, 20.0, 12.0),
                listOf(15.0, 18.0, 24.0, 25.0, 18.0),
            )

        fun spawning(atLevel: Int): List<Rarity> = entries.filter { it.fishUnlockLevel <= atLevel }
    }
}
