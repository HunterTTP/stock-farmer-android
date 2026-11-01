package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.model.GridPoint
import kotlin.math.floor

internal enum class Facing {
    RIGHT,
    UP,
    LEFT,
    DOWN,
    ;

    companion object {
        fun from(direction: GridPoint): Facing? =
            when {
                direction.row == 0 && direction.col > 0 -> RIGHT
                direction.row == 0 && direction.col < 0 -> LEFT
                direction.col == 0 && direction.row < 0 -> UP
                direction.col == 0 && direction.row > 0 -> DOWN
                else -> null
            }
    }
}

internal data class SheetFrame(
    val row: Int,
    val column: Int,
)

internal data class FrameRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** Rows count from the top of the PNG, as do libGDX texture regions. */
internal data class AnimalSheet(
    val frameWidth: Int,
    val rows: Int,
) {
    fun rect(
        frame: SheetFrame,
        width: Int,
        height: Int,
    ): FrameRect {
        require(height % rows == 0)
        val frameHeight = height / rows
        require(frame.row in 0 until rows && frame.column >= 0)
        require((frame.column + 1) * frameWidth <= width)
        return FrameRect(frame.column * frameWidth, frame.row * frameHeight, frameWidth, frameHeight)
    }
}

internal data class AnimationClip(
    val row: Int,
    val firstColumn: Int,
    val count: Int,
    val frameTime: Double,
    val loops: Int = 1,
    val reversed: Boolean = false,
) {
    val duration: Double get() = count * frameTime * loops

    fun frame(seconds: Double): SheetFrame {
        val index = floor(seconds.coerceAtLeast(0.0) / frameTime).toLong() % count
        return SheetFrame(row, firstColumn + if (reversed) count - 1 - index.toInt() else index.toInt())
    }
}

internal fun List<AnimationClip>.frame(seconds: Double): SheetFrame {
    var remaining = seconds.coerceAtLeast(0.0)
    for (clip in this) {
        if (remaining < clip.duration) return clip.frame(remaining)
        remaining -= clip.duration
    }
    return last().frame(last().duration - 0.000001)
}
