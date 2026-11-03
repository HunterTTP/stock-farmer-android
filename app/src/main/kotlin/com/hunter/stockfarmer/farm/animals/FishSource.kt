package com.hunter.stockfarmer.farm.animals

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.secondsSince
import com.hunter.stockfarmer.diagnostics.PerformanceWork
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.farm.Standing
import com.hunter.stockfarmer.farm.StandingSource
import com.hunter.stockfarmer.farm.TextureLibrary
import java.time.Instant

internal class FishSource(
    private val textures: TextureLibrary,
) : StandingSource {
    override val work = PerformanceWork.animals

    private class AnimalTrack(
        val kind: String,
        val size: Double,
        point: GridPoint,
        val appeared: Instant,
    ) {
        val motion = AnimalMotion(point, Facing.LEFT, appeared)
        var orientation = fishOrientation(kind, Facing.LEFT, false)
        var fromAngle = orientation.degrees
        var turned = appeared
        private var region: TextureRegion? = null

        /** The whole picture as a region, kept so a frame allocates none. */
        fun regionOf(texture: Texture): TextureRegion =
            region?.takeIf { it.texture === texture } ?: TextureRegion(texture).also { region = it }
    }

    private val tracks = mutableMapOf<GridPoint, AnimalTrack>()

    override fun collect(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        out: MutableList<Standing>,
    ) {
        val wanted = mutableSetOf<GridPoint>()
        for ((anchor, pond) in state.fishPonds) {
            val fish = pond.fish ?: continue
            val kind = fish.kind ?: continue
            val texture = textures.texture("animals/Fish", kind.assetName) ?: continue
            wanted += anchor
            val old = tracks[anchor]
            val node =
                if (old == null || old.kind != kind.id || old.size != fish.sizeFactor) {
                    AnimalTrack(kind.id, fish.sizeFactor, fish.position, now).also { tracks[anchor] = it }
                } else {
                    old
                }
            val pose = node.motion.update(fish.position, now, duration = 1.8)
            val orientation = fishOrientation(kind.id, pose.facing, node.orientation.mirrored)
            if (orientation != node.orientation) {
                node.fromAngle = turnDegrees(node.fromAngle, node.orientation.degrees, now.secondsSince(node.turned))
                node.orientation = orientation
                node.turned = now
            }
            val age = now.secondsSince(node.appeared)
            val size = 85f * fish.sizeFactor.toFloat() * fishBreathing(age)
            val angle = turnDegrees(node.fromAngle, orientation.degrees, now.secondsSince(node.turned))
            val x = (pose.col + 0.5f) * FarmRenderer.TILE - size / 2
            val y = (GameState.GRID_SIZE - pose.row - 0.5f) * FarmRenderer.TILE - size / 2
            // All fish shadows sit directly over water and below every standing thing, including ducks.
            out +=
                Standing(Float.NEGATIVE_INFINITY) {
                    val oldColor = batch.packedColor
                    try {
                        batch.setColor(0f, 0f, 0f, 0.3f * (age / 0.8).coerceIn(0.0, 1.0).toFloat())
                        batch.draw(
                            node.regionOf(texture),
                            x,
                            y,
                            size / 2,
                            size / 2,
                            size,
                            size,
                            if (orientation.mirrored) -1f else 1f,
                            1f,
                            angle,
                        )
                    } finally {
                        batch.packedColor = oldColor
                    }
                }
        }
        tracks.keys.retainAll(wanted)
    }
}
