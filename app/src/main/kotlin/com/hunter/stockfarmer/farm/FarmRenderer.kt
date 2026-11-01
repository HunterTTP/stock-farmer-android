package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.FenceConnections
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.rules.FruitTreePhase
import com.hunter.stockfarmer.core.rules.GrowthConditions
import com.hunter.stockfarmer.core.rules.GrowthTimelines
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.fullBuildings
import com.hunter.stockfarmer.core.rules.saturatedFarmland
import com.hunter.stockfarmer.core.rules.workshopRecipe
import com.hunter.stockfarmer.game.SceneInteractions
import com.hunter.stockfarmer.ui.screens.PerformanceMonitor
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant

/**
 * Draws the farm from the game state: the ground first, then everything that stands on it, back to front by how far down
 * the map its ground reaches, so a thing set below another is drawn over it. Art is 48 pixels to a tile.
 */
class FarmRenderer(
    private val textures: TextureLibrary,
    private val sources: List<StandingSource> = emptyList(),
    private val groundEffects: ((SpriteBatch, GameState, Instant) -> Unit)? = null,
    private val monitor: PerformanceMonitor? = null,
    private val weathercocks: WeathercockTurn = WeathercockTurn(),
) {
    private val standing = ArrayList<Standing>()
    private val timelines = HashMap<Plot, GrowthTimelines>()
    private val terrain = TerrainTextures(textures)
    val growth = GrowthReveal()
    val interactions = SceneInteractions()
    var gridShown = true
    var cameraScale = 1f
    var maxCameraScale = 1f

    private fun <T> measure(
        work: PerformanceWork,
        action: () -> T,
    ): T = monitor?.measure(work, action) ?: action()

    fun draw(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        ground: ((SpriteBatch, GameState, Instant) -> Unit)? = groundEffects,
    ) {
        val season = state.season
        measure(PerformanceWork.structure) {
            drawGround(batch, state, season)
            if (gridShown) drawGrid(batch, textures, cameraScale, maxCameraScale)
            drawFertilizer(batch, state)
        }
        ground?.invoke(batch, state, now)
        standing.clear()
        growth.prune(state.trees.keys, state.rocks.keys, now)
        measure(PerformanceWork.structure) {
            collectBuildings(batch, state, now)
            collectTrees(batch, state, now)
            collectRocks(batch, state, now)
            collectDecorations(batch, state, now)
            collectFences(batch, state)
        }
        measure(PerformanceWork.crops) {
            collectCrops(batch, state, now)
        }
        sources.forEach { source ->
            measure(source.work) {
                source.collect(batch, state, now, standing)
            }
        }
        standing.sortBy { it.bottomEdge }
        standing.forEach { it.draw() }
    }

    private fun drawGround(
        batch: SpriteBatch,
        state: GameState,
        season: com.hunter.stockfarmer.core.rules.Season,
    ) {
        val grass = textures.texture("terrain", "grass", "jpg", season, smooth = true)
        val farmland = textures.texture("terrain", "farmland", "jpg", smooth = true)
        val saturatedTiles = state.saturatedFarmland
        val saturated = textures.texture("terrain", "farmland_saturated", "jpg", smooth = true)
        for (row in 0 until GameState.GRID_SIZE) {
            for (col in 0 until GameState.GRID_SIZE) {
                grass?.let { batch.draw(it, col * TILE, worldY(row), TILE, TILE) }
            }
        }
        for (point in state.farmland) {
            val texture = if (point in saturatedTiles) saturated else farmland
            texture?.let { batch.draw(it, point.col * TILE, worldY(point.row), TILE, TILE) }
        }
        for ((point, cover) in state.groundCover) {
            terrain
                .cover(cover)
                ?.let { batch.draw(it, point.col * TILE, worldY(point.row), TILE, TILE) }
        }
        for ((point, style) in state.water) {
            batch.color = waterColor(style)
            batch.draw(textures.white, point.col * TILE, worldY(point.row), TILE, TILE)
        }
        batch.color = Color.WHITE
    }

    private fun collectBuildings(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
    ) {
        val fullOrigins = state.fullBuildings(now)
        for (placed in state.placedBuildings) {
            val building: Building = placed.building
            if (building.id == "tractor1") continue
            val isFull = placed.origin in fullOrigins
            val assetName = if (isFull) building.fullAssetName ?: building.assetName else building.assetName
            val texture = textures.texture("buildings", assetName, season = state.season) ?: continue
            val box =
                StandingLayout.building(
                    placed.origin,
                    building.width,
                    building.height,
                    texture.width.toFloat(),
                    texture.height.toFloat(),
                )
            val (x, y) = box
            standing +=
                Standing((placed.origin.row + building.height).toFloat()) {
                    val working =
                        state.workshops[placed.origin]?.let { batchWork ->
                            state.workshopRecipe(placed.origin)?.let { !batchWork.isFinished(it, now) }
                        } ?: false
                    if (building.id == "dairy" && working) {
                        val sheet = textures.texture("motion", "dairyWork")
                        if (sheet != null) {
                            val frameIndex = ((now.toEpochMilli() / 140) % 7).toInt()
                            val frameWidth = sheet.width / 7
                            val dairyRegion = TextureRegion(sheet, frameIndex * frameWidth, 0, frameWidth, sheet.height)
                            batch.draw(dairyRegion, x, y, box.width, box.height)
                        } else {
                            batch.draw(texture, x, y, box.width, box.height)
                        }
                    } else {
                        batch.draw(texture, x, y, box.width, box.height)
                    }
                    if (building.id == "stoneOven" && working) {
                        val sheet = textures.texture("motion", "ovenSmoke")
                        if (sheet != null) {
                            val frameIndex = ((now.toEpochMilli() / 160) % 8).toInt()
                            val frameWidth = sheet.width / 8
                            val smokeRegion = TextureRegion(sheet, frameIndex * frameWidth, 0, frameWidth, sheet.height)
                            val scale = box.width / 240f
                            val smokeWidth = 144f * scale
                            val smokeHeight = 192f * scale
                            val smokeX = x + box.width / 2f + 60f * scale - smokeWidth / 2f
                            val smokeY = y + box.height / 2f + 48f * scale
                            batch.draw(smokeRegion, smokeX, smokeY, smokeWidth, smokeHeight)
                        }
                    }
                    if (building.assetName == "silo_tower") {
                        val index = interactions.door(placed.origin, now)
                        val sheet = textures.texture("motion", "siloDoor")
                        if (index != null && sheet != null) {
                            val scale = box.height / 672
                            val frameWidth = sheet.width / 14
                            val door = TextureRegion(sheet, index * frameWidth, 0, frameWidth, sheet.height)
                            batch.draw(door, x + box.width / 2 - 24 * scale, y, 48 * scale, 96 * scale)
                        }
                    }
                }
        }
    }

    private fun collectTrees(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
    ) {
        for ((origin, placed) in state.trees) {
            val tree = placed.tree
            val texture = textures.texture("trees", tree.assetName(placed.color), season = state.season) ?: continue
            val (x, y, width, height) =
                StandingLayout.standing(
                    origin,
                    tree.side,
                    tree.side,
                    texture.width.toFloat(),
                    texture.height.toFloat(),
                    StandingLayout.LARGE_PIXELS_PER_TILE,
                )
            val depth = (origin.row + tree.side).toFloat() + TREE_OVER
            val alpha = growth.collect(batch, textures, origin, true, now, depth, x, y, width, height, standing)
            standing +=
                Standing(depth) {
                    val old = batch.packedColor
                    batch.setColor(1f, 1f, 1f, alpha)
                    batch.draw(texture, x, y, width, height)
                    batch.packedColor = old
                }
        }
    }

    private fun collectRocks(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
    ) {
        for ((point, size) in state.rocks) {
            val texture = textures.texture("rocks", size.assetName, season = state.season) ?: continue
            val (x, y, width, height) =
                StandingLayout.standing(
                    point,
                    1,
                    1,
                    texture.width.toFloat(),
                    texture.height.toFloat(),
                    StandingLayout.LARGE_PIXELS_PER_TILE,
                    size.spriteScale.toFloat(),
                )
            val depth = point.row + 1f
            val alpha = growth.collect(batch, textures, point, false, now, depth, x, y, width, height, standing)
            standing +=
                Standing(depth) {
                    val old = batch.packedColor
                    batch.setColor(1f, 1f, 1f, alpha)
                    batch.draw(texture, x, y, width, height)
                    batch.packedColor = old
                }
        }
    }

    private fun collectDecorations(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
    ) {
        for ((origin, placed) in state.decorations) {
            val decoration = Decoration.named(placed.decorationID) ?: continue
            val texture =
                textures.texture(
                    decoration.imageFolder,
                    decoration.assetNames[decoration.clampedSize(placed.size)],
                    season = state.season,
                )
                    ?: continue
            val scale = TILE / StandingLayout.PIXELS_PER_TILE * decoration.drawScale.toFloat()
            val picture = texture.width.toFloat() to texture.height.toFloat()
            val (x, y, width, height) =
                if (decoration.isSprinkler) {
                    StandingLayout.lying(
                        origin,
                        decoration.width,
                        decoration.height,
                        picture.first,
                        picture.second,
                        decoration.drawScale.toFloat(),
                    )
                } else {
                    StandingLayout.standing(
                        origin,
                        decoration.width,
                        decoration.height,
                        picture.first,
                        picture.second,
                        StandingLayout.PIXELS_PER_TILE,
                        decoration.drawScale.toFloat(),
                    )
                }
            standing +=
                Standing((origin.row + decoration.height).toFloat()) {
                    val motion = interactions.decoration(origin, placed, state.season, now)
                    val sheet = motion?.let { textures.texture("motion", it.name) }
                    if (motion != null && sheet != null) {
                        val frameWidth = sheet.width / motion.count
                        val frame = TextureRegion(sheet, motion.frame * frameWidth, 0, frameWidth, sheet.height)
                        val animatedWidth = motion.width * scale
                        val animatedHeight = motion.height * scale
                        val center = x + width / 2 + if (motion.name == "well") 24 * scale else 0f
                        batch.draw(frame, center - animatedWidth / 2, y, animatedWidth, animatedHeight)
                    } else {
                        val rotation =
                            if (state.season == Season.winter) {
                                interactions.winterRotation(origin, placed, now)
                            } else {
                                0f
                            }
                        val turn =
                            if (placed.decorationID ==
                                WEATHERCOCK
                            ) {
                                weathercocks.scaleX(origin, x + width / 2, now)
                            } else {
                                1f
                            }
                        batch.draw(TextureRegion(texture), x, y, width / 2, 0f, width, height, turn, 1f, rotation)
                    }
                }
        }
    }

    /** Each fence tile is a three by three grid of small pictures: its own piece in the middle and arms toward its joined neighbors. */
    private fun collectFences(
        batch: SpriteBatch,
        state: GameState,
    ) {
        val spacing = TILE / 3
        val size = StandingLayout.fencePictureSize
        for ((point, colorID) in state.fences) {
            val color = FenceColor.named(colorID) ?: FenceColor.starter
            val connections = FenceConnections(point, state.fences)
            val centerX = (point.col + 0.5f) * TILE
            val centerY = worldY(point.row) + TILE / 2
            val placements = StandingLayout.fenceDrawOrder(connections)
            standing +=
                Standing(point.row + 1f + FENCE_OVER) {
                    for (placement in placements) {
                        val texture =
                            textures.texture(
                                "decorations/${color.folder}",
                                "fence-${placement.piece.rawValue}",
                                season = state.season,
                            )
                        texture?.let {
                            val ground = centerY - placement.row * spacing - spacing / 2
                            batch.draw(it, centerX + placement.column * spacing - size / 2, ground, size, size)
                        }
                    }
                }
        }
    }

    private fun collectCrops(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
    ) {
        if (state.plots.isEmpty()) return
        val conditions = GrowthConditions(state)
        timelines.keys.retainAll(state.plots.values.toSet())
        for ((point, plot) in state.plots) {
            val crop = Crop.named(plot.cropID) ?: continue
            if (!plot.isAnchor(point)) continue
            val progress = conditions.progress(plot, crop, point, now)
            if (crop.isFruitTree) {
                collectFruitTree(batch, plot, crop, point, progress, state.season)
            } else {
                collectPlanting(batch, plot, crop, point, progress)
            }
        }
    }

    /** A crop is four small plants, each growing a little differently and eaten from the bottom right. */
    private fun collectPlanting(
        batch: SpriteBatch,
        plot: Plot,
        crop: Crop,
        point: GridPoint,
        progress: Double,
    ) {
        val growth = timelines.getOrPut(plot) { GrowthTimelines(plot, point, crop) }
        for (quadrant in 0 until QUADRANTS) {
            if (quadrant >= QUADRANTS - plot.servingsEaten) continue
            val phase = growth.phase(quadrant, progress)
            val texture = textures.texture("crops", "${crop.id}-phase-${phase.spriteNumber}") ?: continue
            val (x, y, width, height) = StandingLayout.quadrant(point, quadrant)
            val near = quadrant >= 2
            standing +=
                Standing(point.row + 1f + if (near) NEAR_HALF else 0f) {
                    batch.draw(texture, x, y, width, height)
                }
        }
    }

    private fun collectFruitTree(
        batch: SpriteBatch,
        plot: Plot,
        crop: Crop,
        point: GridPoint,
        progress: Double,
        season: com.hunter.stockfarmer.core.rules.Season,
    ) {
        val origin = plot.origin ?: point
        val phase = FruitTreePhase.ofProgress(progress)
        val texture = textures.texture("crops/fruit_trees", crop.treeAssetName(phase), season = season) ?: return
        val (x, y, width, height) =
            StandingLayout.fruitTree(origin, crop.side, texture.width.toFloat(), texture.height.toFloat())
        standing +=
            Standing(origin.row + crop.side.toFloat() + TREE_OVER) {
                batch.draw(texture, x, y, width, height)
            }
    }

    private fun drawFertilizer(
        batch: SpriteBatch,
        state: GameState,
    ) {
        val texture = textures.texture("tools", "fertilizer") ?: return
        val side = TILE / 2 * 0.7f
        for (tile in state.fertilized) {
            for (row in 0..1) {
                for (col in 0..1) {
                    batch.draw(
                        texture,
                        (tile.col + 0.25f + col * 0.5f) * TILE - side / 2,
                        worldY(tile.row) + (0.25f + row * 0.5f) * TILE - side / 2,
                        side,
                        side,
                    )
                }
            }
        }
    }

    private fun waterColor(style: WaterStyle): Color =
        when (style) {
            WaterStyle.deep -> Color(0.13f, 0.38f, 0.62f, 1f)
            WaterStyle.medium -> Color(0.22f, 0.52f, 0.74f, 1f)
            WaterStyle.shallow -> Color(0.45f, 0.72f, 0.86f, 1f)
        }

    companion object {
        /** A tile's side in world units; the map is [GameState.GRID_SIZE] tiles square. */
        const val TILE = 100f
        const val WORLD = GameState.GRID_SIZE * TILE
        private const val QUADRANTS = 4

        // Standing uses rows rather than SpriteKit's thousandths, so its local depth offsets scale with the rows.
        private const val NEAR_HALF = 0.6f
        private const val TREE_OVER = 0.8f
        private const val FENCE_OVER = 0.1f
        private const val WEATHERCOCK = "weathercock"

        /** The world y of the bottom edge of a map row; rows count down the map while world y counts up. */
        fun worldY(row: Int): Float = (GameState.GRID_SIZE - 1 - row) * TILE
    }
}
