package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.catalog.Crop

internal data class ScreenArt(
    val path: String,
    val frameWidth: Int? = null,
    val frameHeight: Int? = null,
    val columns: Int = 1,
    val rows: Int = 1,
    val column: Int = 0,
    val row: Int = 0,
) {
    companion object {
        fun image(
            folder: String,
            name: String,
        ): ScreenArt = ScreenArt("images/$folder/$name.png")

        fun crop(crop: Crop): ScreenArt =
            if (crop.isFruitTree) {
                image("crops/fruit_trees", crop.basketAssetName)
            } else {
                image("crops", "${crop.id}-phase-4")
            }
    }
}
