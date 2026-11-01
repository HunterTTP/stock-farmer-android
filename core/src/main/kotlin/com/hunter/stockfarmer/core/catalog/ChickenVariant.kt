package com.hunter.stockfarmer.core.catalog

import kotlinx.serialization.Serializable

@Serializable
data class ChickenVariant(
    val id: String,
    val name: String,
    val assetName: String,
) {
    val isRooster: Boolean get() = id.startsWith("cock-")

    companion object {
        val all =
            listOf(
                ChickenVariant("hen-brown", "Brown Hen", "Chicken_Brown_48x48"),
                ChickenVariant("hen-brown2", "Dark Brown Hen", "Chicken_Brown_2_48x48"),
                ChickenVariant("hen-blackBrown", "Black and Brown Hen", "Chicken_Black_and_Brown_48x48"),
                ChickenVariant("hen-golden", "Golden Hen", "Chicken_Golden_48x48"),
                ChickenVariant("hen-gray", "Gray Hen", "Chicken_Gray_48x48"),
                ChickenVariant("hen-white", "White Hen", "Chicken_White_48x48"),
                ChickenVariant("hen-yellow", "Yellow Hen", "Chicken_Yellow_48x48"),
            )
        val roosters =
            all.map {
                ChickenVariant(
                    it.id.replace("hen-", "cock-"),
                    it.name.replace("Hen", "Rooster"),
                    it.assetName.replace("Chicken_", "Rooster_"),
                )
            }
        val available = all + roosters

        /** A rooster in an old save is the hen of the same color. */
        fun named(id: String): ChickenVariant? = available.firstOrNull { it.id == id.replace("rooster-", "hen-") }
    }
}
