@file:Suppress("ktlint:standard:class-naming", "ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class WaterStyle(
    val displayName: String,
) {
    deep("Deep"),
    medium("Medium"),
    shallow("Shallow"),
    ;

    val id: WaterStyle get() = this
    val rawValue: String get() = name
}

@Serializable
sealed interface LandscapeTool {
    @Serializable data class groundCover(
        val cover: GroundCover,
    ) : LandscapeTool

    @Serializable data object farmland : LandscapeTool

    @Serializable data object water : LandscapeTool

    @Serializable data object grass : LandscapeTool

    @Serializable data object tree : LandscapeTool

    val id: LandscapeTool get() = this
    val name: String get() =
        when (this) {
            is groundCover -> cover.displayName
            farmland -> "Farmland"
            water -> "Water"
            grass -> "Grass"
            tree -> "Tree"
        }

    /** Paths and grass can be laid or cleared beneath a standing structure. */
    val fitsUnderAStructure: Boolean get() = this is groundCover || this == grass

    companion object {
        val waterCost = 0.0
    }
}
