package com.hunter.stockfarmer.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
        ),
    )

    override fun deserialize(decoder: Decoder): TractorRun {
        val saved = decoder.decodeSerializableValue(Saved.serializer())
        if (!GameState.contains(saved.position) || saved.nextTile?.let { !GameState.contains(it) } == true) {
            throw SerializationException("Invalid Tractor position")
        }
        val direction = saved.nextTile?.let { GridPoint(it.row - saved.position.row, it.col - saved.position.col) }
        val facing = saved.facing ?: direction?.let(TractorFacing::ofDirection) ?: TractorFacing.right
        return TractorRun(
            saved.home,
            saved.cropID,
            saved.position,
            saved.nextTile,
            saved.nextPoseFacing,
            facing,
            saved.progress,
            saved.returning,
            saved.remaining,
        )
    }
}
