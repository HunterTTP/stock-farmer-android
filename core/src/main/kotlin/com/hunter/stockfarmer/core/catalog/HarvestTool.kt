@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class HarvestTool(
    val displayName: String,
) {
    scythe("Scythe"),
    axe("Axe"),
    pickaxe("Pickaxe"),
    bucket("Bucket"),
    rod("Fishing Rod"),
    ;

    val id: HarvestTool get() = this
    val rawValue: String get() = name

    /** Logs and stones sell for nothing, so they are stored. */
    val canSell: Boolean get() = this != axe && this != pickaxe && this != rod
}
