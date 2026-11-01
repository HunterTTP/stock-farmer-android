package com.hunter.stockfarmer.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.abs
import kotlin.math.max

object TractorRunSerializer : KSerializer<TractorRun> {
    @Serializable
    private data class Saved(
        val home: GridPoint,
        val cropID: String,
        val position: GridPoint,
        val nextTile: GridPoint? = null,
        val nextPoseFacing: TractorFacing? = null,
        val facing: TractorFacing? = null,
        val progress: Double = 0.0,
        val returning: Boolean = false,
        val remaining: Set<GridPoint> = emptySet(),
        val version: Int = 1,
        val selling: Boolean? = null,
    )

    override val descriptor = Saved.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: TractorRun,
    ) = encoder.encodeSerializableValue(
        Saved.serializer(),
        Saved(
            value.home,
            value.cropID,
            value.position,
            value.nextTile,
            value.nextPoseFacing,
            value.facing,
            value.progress,
            value.returning,
            value.remaining,
            version = 5,
        ),
    )

    override fun deserialize(decoder: Decoder): TractorRun {
        val saved = decoder.decodeSerializableValue(Saved.serializer())
        if (saved.version !in 1..5) throw SerializationException("Unsupported Tractor trip")
        if (!GameState.contains(saved.position) || saved.nextTile?.let { !GameState.contains(it) } == true) {
            throw SerializationException("Invalid Tractor position")
        }
        val direction = saved.nextTile?.let { GridPoint(it.row - saved.position.row, it.col - saved.position.col) }
        val facing = saved.facing ?: direction?.let(TractorFacing::ofDirection) ?: TractorFacing.right
        val head = if (saved.version == 1) TractorRules.headTile(saved.position, facing) else saved.position
        val next = if (saved.version == 1) saved.nextTile?.let { TractorRules.headTile(it, facing) } else saved.nextTile
        var run =
            TractorRun(
                saved.home,
                saved.cropID,
                head,
                next,
                saved.nextPoseFacing,
                facing,
                saved.progress,
                saved.returning,
                saved.remaining,
            )
        if (saved.version < 5) {
            fun validFootprint(
                point: GridPoint,
                heading: TractorFacing,
            ): Boolean {
                val horizontal = heading.direction.col != 0
                val width =
                    if (horizontal) {
                        if (saved.version < 3) {
                            2
                        } else {
                            3
                        }
                    } else {
                        1
                    }
                val height =
                    if (horizontal) {
                        if (saved.version == 3) {
                            2
                        } else {
                            1
                        }
                    } else if (saved.version < 4) {
                        2
                    } else {
                        3
                    }
                val origin =
                    GridPoint(
                        point.row - (height - 1) * max(0, heading.direction.row),
                        point.col - (width - 1) * max(0, heading.direction.col),
                    )
                return origin.rectangle(height, width).all(GameState::contains)
            }
            val distance = next?.let { abs(it.row - head.row) + abs(it.col - head.col) }
            val validStep =
                if (next == null) {
                    saved.progress == 0.0 && saved.nextPoseFacing == null
                } else {
                    distance in 1..(if (saved.version < 3) 1 else 2) &&
                        (next.row == head.row || next.col == head.col) && validFootprint(next, run.destinationFacing)
                }
            if (!validFootprint(head, facing) || !saved.progress.isFinite() || saved.progress !in 0.0..<1.0 ||
                !validStep
            ) {
                throw SerializationException("Invalid legacy Tractor trip")
            }
            if (saved.version >= 3 && next != null && (run.destinationFacing != facing || distance!! > 1)) {
                run = run.copy(nextTile = null, nextPoseFacing = null, progress = 0.0)
            }
        }
        return run
    }
}
