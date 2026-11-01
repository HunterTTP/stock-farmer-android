package com.hunter.stockfarmer.core.catalog

import kotlinx.serialization.Serializable

@Serializable
data class FenceColor(
    val id: String,
    val name: String,
    val folder: String,
) {
    companion object {
        val all = listOf(FenceColor("wood", "Wood", "Fences"))
        val retiredIDs =
            setOf(
                "darkWood",
                "rustic",
                "picket",
                "lightPicket",
                "goldenPicket",
                "redPicket",
                "whiteRail",
                "brownRail",
                "darkRail",
                "woodGate",
                "darkWoodGate",
                "rusticGate",
            )
        val starter = all[0]
        val price = 2.0

        fun named(id: String): FenceColor? = all.firstOrNull { it.id == id }
    }
}
