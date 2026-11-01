@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class RockSize {
    small,
    medium,
    big,
    copper,
    smallSilver,
    silver,
    gold,
    ruby,
    sapphire,
    ;

    val rawValue: String get() = name
    val ore: OreKind? get() =
        when (this) {
            copper -> OreKind.copper
            smallSilver, silver -> OreKind.silver
            gold -> OreKind.gold
            ruby -> OreKind.ruby
            sapphire -> OreKind.sapphire
            else -> null
        }
    val displayName: String get() =
        if (this ==
            smallSilver
        ) {
            "Small Silver Vein"
        } else {
            ore?.displayName ?: name.replaceFirstChar { it.uppercaseChar() }
        }
    val stones: Int get() =
        when (this) {
            small -> 2
            medium -> 4
            else -> 6
        }
    val weight: Double get() =
        when (this) {
            small -> 0.5
            medium -> 0.35
            big -> 0.15
            else -> 0.0
        }
    val spriteScale: Double get() =
        when (this) {
            medium -> 1.25
            big -> 1.25 * 1.25
            else -> 1.0
        }
    val hitsToBreak: Int get() = if (ore == null) stones * 2 else stones
    val assetName: String get() =
        when (this) {
            copper -> "Rock_Small_Bronze_Stone_48x48"
            smallSilver -> "Rock_Small_Silver_Stone_48x48"
            silver -> "Rock_Medium_Silver_Stone_48x48"
            gold -> "Rock_Medium_Gold_Stone_48x48"
            ruby -> "Rock_Big_REd_Stone_48x48"
            sapphire -> "Rock_Big_Blue_Stone_48x48"
            else -> "Rock_${name.replaceFirstChar { it.uppercaseChar() }}_48x48"
        }

    companion object {
        val stoneSizes = listOf(small, medium, big)
        val oreSizes = listOf(copper, smallSilver, silver, gold, ruby, sapphire)
    }
}

object Rock {
    /** Stones are for building, so selling them earns nothing. */
    val stoneSalePrice = 0.0
    val onANewMap = 40..50
}

object OreVein {
    val onANewMap = 3..5
    val maximumOnMap = 5
}
