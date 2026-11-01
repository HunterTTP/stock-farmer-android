package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant
import kotlin.math.abs
import kotlin.random.Random

internal data class AnimalPose(
    val row: Float,
    val col: Float,
    val facing: Facing,
    val mirrored: Boolean,
    val walking: Boolean,
    val elapsed: Double,
)

/** Only interpolates steps already made by the game; rendering never chooses or applies game actions. */
internal class AnimalMotion(
    point: GridPoint,
    facing: Facing,
    now: Instant,
) {
    private var point = point
    private var fromRow = point.row.toFloat()
    private var fromCol = point.col.toFloat()
    private var facing = facing
    private var mirrored = false
    private var started = now
    private var moving = false

    fun update(
        target: GridPoint,
        now: Instant,
        duration: Double = AnimalSprites.STEP_DURATION,
    ): AnimalPose {
        if (now < started) {
            point = target
            fromRow = target.row.toFloat()
            fromCol = target.col.toFloat()
            started = now
            moving = false
        }
        if (target != point) {
            val previous = pose(now, duration)
            val direction = GridPoint(target.row - point.row, target.col - point.col)
            Facing.from(direction)?.let { facing = it }
            if (facing == Facing.LEFT) mirrored = true
            if (facing == Facing.RIGHT) mirrored = false
            moving = abs(direction.row) + abs(direction.col) == 1
            fromRow = if (moving) previous.row else target.row.toFloat()
            fromCol = if (moving) previous.col else target.col.toFloat()
            point = target
            started = now
        }
        return pose(now, duration)
    }

    private fun pose(
        now: Instant,
        duration: Double,
    ): AnimalPose {
        val elapsed = now.secondsSince(started).coerceAtLeast(0.0)
        val progress = if (moving) (elapsed / duration).coerceIn(0.0, 1.0).toFloat() else 1f
        return AnimalPose(
            fromRow + (point.row - fromRow) * progress,
            fromCol + (point.col - fromCol) * progress,
            facing,
            mirrored,
            moving && elapsed < duration,
            if (moving && elapsed >= duration) elapsed - duration else elapsed,
        )
    }
}

internal class AnimalAnimation(
    seed: Int,
) {
    private val random = Random(seed)
    private var sprites: AnimalSprites? = null
    private var facing: Facing? = null
    private var clips = emptyList<AnimationClip>()
    private var began = 0.0

    fun frame(
        look: AnimalSprites,
        pose: AnimalPose,
    ): SheetFrame {
        if (pose.walking) {
            clips = emptyList()
            return look.walk(pose.facing).frame(pose.elapsed)
        }
        if (look != sprites || pose.facing != facing || clips.isEmpty() || pose.elapsed < began) {
            sprites = look
            facing = pose.facing
            clips = look.rest(1, pose.facing, random)
            began = pose.elapsed
        }
        if (pose.elapsed - began >= clips.sumOf { it.duration }) {
            clips = look.rest(0, pose.facing, random)
            began = pose.elapsed
        }
        return clips.frame(pose.elapsed - began)
    }
}
