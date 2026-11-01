package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.Texture
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceConnections
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.FruitTreePhase
import com.hunter.stockfarmer.farm.TextureLibrary

internal data class ShadowCaster(
    val foot: Point,
    val shape: ShadowShape,
    val texture: Texture? = null,
)

internal class ShadowCasters(
    private val textures: TextureLibrary,
) {
    fun collect(state: GameState): List<ShadowCaster> =
        buildList {
            fun foot(
                origin: GridPoint,
                columns: Int,
                rows: Int,
            ) = Point((origin.col + columns / 2.0) * TILE, WORLD - (origin.row + rows) * TILE)

            fun picture(
                folder: String,
                name: String,
                kind: ShadowKind,
                foot: Point,
                scale: Double,
            ) {
                val texture = textures.texture(folder, name, season = state.season) ?: return
                val shape =
                    ShadowShape(kind, texture.width * scale, texture.height * scale, "${state.season}/$folder/$name")
                add(ShadowCaster(foot, shape, texture))
            }
            for (placed in state.placedBuildings) {
                val building = placed.building
                val texture = textures.texture("buildings", building.assetName, season = state.season) ?: continue
                val scale = minOf(building.width * TILE / texture.width, building.height * TILE / texture.height)
                val width = texture.width * scale
                val height = texture.height * scale
                val center = foot(placed.origin, building.width, building.height)
                val feet = Point(center.x, center.y + (building.height * TILE - height) / 2)
                add(ShadowCaster(feet, ShadowShape(ShadowKind.BUILDING, width, height)))
            }
            for ((origin, placed) in state.trees) {
                picture(
                    "trees",
                    placed.tree.assetName(placed.color),
                    ShadowKind.TREE,
                    foot(origin, placed.tree.side, placed.tree.side),
                    TILE / 96,
                )
            }
            for ((origin, size) in state.rocks) {
                picture("rocks", size.assetName, ShadowKind.ROCK, foot(origin, 1, 1), TILE / 96 * size.spriteScale)
            }
            for ((origin, placed) in state.decorations) {
                val decoration = Decoration.named(placed.decorationID) ?: continue
                if (decoration.isSprinkler) continue
                picture(
                    decoration.imageFolder,
                    decoration.assetNames[decoration.clampedSize(placed.size)],
                    ShadowKind.DECORATION,
                    foot(origin, decoration.width, decoration.height),
                    TILE / 48 * decoration.drawScale,
                )
            }
            for ((point, plot) in state.plots) {
                val crop = Crop.named(plot.cropID) ?: continue
                if (!crop.isFruitTree || !plot.isAnchor(point)) continue
                val name = crop.treeAssetName(FruitTreePhase.ripe)
                val texture = textures.texture("crops/fruit_trees", name, season = state.season) ?: continue
                val scale = minOf(crop.side * TILE * 0.95 / texture.width, crop.side * TILE * 0.95 / texture.height)
                picture(
                    "crops/fruit_trees",
                    name,
                    ShadowKind.FRUIT_TREE,
                    foot(plot.origin ?: point, crop.side, crop.side),
                    scale,
                )
            }
            for (point in state.fences.keys) {
                val joining = FenceConnections(point, state.fences)
                val ground = WORLD - (point.row + 0.5) * TILE - TILE / 6
                val lowest = if (joining.south) ground - TILE / 3 else ground
                val left = if (joining.west) -TILE / 2 else -TILE * 0.06
                val right = if (joining.east) TILE / 2 else TILE * 0.06
                val back = ground - lowest + (if (joining.north) TILE / 3 else 0.0) + TILE * 0.12
                add(
                    ShadowCaster(
                        Point((point.col + 0.5) * TILE, lowest),
                        ShadowShape(
                            ShadowKind.FENCE,
                            right - left,
                            TILE * 0.4,
                            baseLeft = left,
                            baseHeight = back,
                        ),
                    ),
                )
            }
        }
}
