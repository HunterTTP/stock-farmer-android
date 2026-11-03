@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.catalog

enum class OreKind {
    copper,
    silver,
    gold,
    ruby,
    sapphire,
    ;

    val rawValue: String get() = name
    val displayName: String get() =
        name.replaceFirstChar { it.uppercaseChar() } +
            if (this == ruby || this == sapphire) " Rough" else " Ore"
    val value: Double get() = 8.0
    val rock: RockSize get() = RockSize.valueOf(rawValue)
}
