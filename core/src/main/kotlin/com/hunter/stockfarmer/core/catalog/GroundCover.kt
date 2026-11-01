@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.catalog

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = GroundCoverSerializer::class)
enum class GroundCover(
    val displayName: String,
) {
    dirtPath("Dirt Path"),
    cobblestonePath("Cobblestone Path"),
    ;

    val id: GroundCover get() = this
    val rawValue: String get() = name
    val cost: Double get() = 2.0
    val assetName: String get() = rawValue
}

object GroundCoverSerializer : KSerializer<GroundCover> {
    override val descriptor = PrimitiveSerialDescriptor("GroundCover", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: GroundCover,
    ) = encoder.encodeString(value.rawValue)

    override fun deserialize(decoder: Decoder): GroundCover =
        when (val saved = decoder.decodeString()) {
            "cobblestonePath" -> GroundCover.cobblestonePath
            "dirtPath", "sandPath", "raisedBed", "barnFloor", "stableFloor" -> GroundCover.dirtPath
            else -> throw SerializationException("Unknown ground cover: $saved")
        }
}
