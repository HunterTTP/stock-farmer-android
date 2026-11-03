package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.rules.FruitTreePhase
import com.hunter.stockfarmer.core.rules.GrowthPhase
import com.hunter.stockfarmer.game.TILE

internal class ProduceBadge(
    private val textures: TextureLibrary,
) {
    private val labels = mutableMapOf<Pair<String, Boolean>, Texture>()
    private var loadText: String? = null

    fun draw(
        batch: SpriteBatch,
        item: StorageItem,
        x: Float,
        y: Float,
    ) {
        val texture =
            when (item) {
                StorageItem.Egg -> {
                    textures.texture("animals/Chickens", "Egg_Big_48x48")
                }

                StorageItem.Fertilizer -> {
                    textures.texture("tools", "fertilizer")
                }

                StorageItem.Wool -> {
                    label("🧶", false, SIZE)
                }

                is StorageItem.CropItem -> {
                    if (item.crop.isFruitTree) {
                        textures.texture("crops/fruit_trees", item.crop.treeAssetName(FruitTreePhase.ripe))
                    } else {
                        textures.texture("crops", "${item.crop.id}-phase-${GrowthPhase.ready.spriteNumber}")
                    }
                }

                else -> {
                    textures.texture("animals/Cows", "Milk_Can_2_48x48")
                }
            } ?: return
        val size = if (item == StorageItem.Egg) SIZE * 0.54f else SIZE
        val scale = if (item == StorageItem.Wool) 1f else size / maxOf(texture.width, texture.height)
        val width = texture.width * scale
        val height = texture.height * scale
        batch.draw(texture, x - width / 2, y - height / 2, width, height)
    }

    fun load(
        batch: SpriteBatch,
        item: StorageItem.CropItem,
        count: Int,
        x: Float,
        y: Float,
    ) {
        val text =
            "×" +
                java.text.NumberFormat
                    .getIntegerInstance(java.util.Locale.US)
                    .format(count)
        if (loadText != text) {
            loadText?.let { previous ->
                for (black in listOf(false, true)) {
                    labels.remove(previous to black)
                    textures.releaseGenerated("label/$previous/${SIZE * 0.7f}/$black")
                }
            }
            loadText = text
        }
        val label = label(text, false, SIZE * 0.7f)
        val shadow = label(text, true, SIZE * 0.7f)
        val gap = SIZE * 0.2f
        val iconX = x - (label.width + gap) / 2
        val textX = iconX + SIZE / 2 + gap
        batch.draw(shadow, textX + 2, y - label.height / 2 - 2)
        batch.draw(label, textX, y - label.height / 2)
        draw(batch, item, iconX, y)
    }

    private fun label(
        text: String,
        black: Boolean,
        size: Float,
    ): Texture = labels.getOrPut(text to black) { textures.label(text, size, black) }

    companion object {
        const val SIZE = TILE * 0.34f
    }
}
