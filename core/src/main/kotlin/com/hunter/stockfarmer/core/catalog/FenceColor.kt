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
        val starter = all[0]
        val price = 2.0

        fun named(id: String): FenceColor? = all.firstOrNull { it.id == id }
    }
}
