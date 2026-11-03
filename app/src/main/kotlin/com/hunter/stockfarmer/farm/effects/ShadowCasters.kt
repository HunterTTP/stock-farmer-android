package com.hunter.stockfarmer.farm.effects

import com.badlogic.gdx.graphics.Texture
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceConnections
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.FruitTreePhase
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.farm.MovingOrigins
import com.hunter.stockfarmer.farm.TextureLibrary

internal data class ShadowCaster(
    val foot: Point,
    val shape: ShadowShape,
    val texture: Texture? = null,
)

internal class ShadowCasters(
    private val textures: TextureLibrary,
) {
    // A picture's key is built once, not for every caster every frame.
    private val imageKeys = Array(Season.entries.size) { HashMap<String, HashMap<String, String>>() }

    /** The shadows of everything standing but the things at [moving], which are being carried to a new spot. */
    fun collect(
        state: GameState,
        moving: MovingOrigins = MovingOrigins.NONE,
    ): List<ShadowCaster> =
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
                val image =
                    imageKeys[state.season.ordinal]
                        .getOrPut(folder) {
                            HashMap()
                        }.getOrPut(name) { "${state.season}/$folder/$name" }
                val shape = ShadowShape(kind, texture.width * scale, texture.height * scale, image)
                add(ShadowCaster(foot, shape, texture))
            }
            for (placed in state.shadowCastingBuildings(moving)) {
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
                if (origin == moving.tree) continue
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
                if (origin == moving.decoration) continue
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
                if ((plot.origin ?: point) == moving.fruitTree) continue
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

/**
 * Everything [ShadowCasters.collect] reads, compared exactly and in order each frame without allocating, so the casters
 * are rebuilt only when one of them could come out differently. Catalog entries and placed values are immutable, so an
 * unchanged reference is an unchanged value; a new reference with equal contents costs only a needless rebuild.
 */
internal class ShadowCasterInputs {
    private var numbers = IntArray(256)
    private var objects = arrayOfNulls<Any>(256)
    private var numberCount = 0
    private var objectCount = 0
    private var numbersSeen = 0
    private var objectsSeen = 0
    private var differs = false

    fun changed(
        state: GameState,
        moving: MovingOrigins,
        sunStep: Int,
    ): Boolean {
        numbersSeen = 0
        objectsSeen = 0
        differs = false
        number(sunStep)
        number(state.season.ordinal)
        point(moving.building)
        point(moving.fruitTree)
        point(moving.decoration)
        point(moving.tree)
        point(state.tractorRun?.home)
        number(state.placedBuildings.size)
        for (placed in state.placedBuildings) {
            point(placed.origin)
            reference(placed.building)
        }
        number(state.trees.size)
        for ((origin, placed) in state.trees) {
            point(origin)
            reference(placed)
        }
        number(state.rocks.size)
        for ((origin, size) in state.rocks) {
            point(origin)
            number(size.ordinal)
        }
        number(state.decorations.size)
        for ((origin, placed) in state.decorations) {
            point(origin)
            reference(placed)
        }
        // Only which tiles hold a fence shapes its shadow, not the fence's color.
        number(state.fences.size)
        for (tile in state.fences.keys) point(tile)
        for ((tile, plot) in state.plots) {
            val crop = Crop.named(plot.cropID) ?: continue
            if (!crop.isFruitTree || !plot.isAnchor(tile)) continue
            point(tile)
            point(plot.origin)
            reference(crop)
        }
        if (numbersSeen != numberCount || objectsSeen != objectCount) differs = true
        numberCount = numbersSeen
        objectCount = objectsSeen
        return differs
    }

    private fun point(tile: GridPoint?) = number(if (tile == null) Int.MIN_VALUE else (tile.row shl 16) xor tile.col)

    private fun number(value: Int) {
        if (numbersSeen == numbers.size) numbers = numbers.copyOf(numbersSeen * 2)
        if (numbersSeen >= numberCount || numbers[numbersSeen] != value) {
            numbers[numbersSeen] = value
            differs = true
        }
        numbersSeen++
    }

    private fun reference(value: Any) {
        if (objectsSeen == objects.size) objects = objects.copyOf(objectsSeen * 2)
        if (objectsSeen >= objectCount || objects[objectsSeen] !== value) {
            objects[objectsSeen] = value
            differs = true
        }
        objectsSeen++
    }
}

/** The buildings that cast a shadow: a Tractor out driving casts its own, so the home it left casts none, and one being moved casts none where it stood. */
internal fun GameState.shadowCastingBuildings(moving: MovingOrigins = MovingOrigins.NONE) =
    placedBuildings.filter { it.origin != tractorRun?.home && it.origin != moving.building }
