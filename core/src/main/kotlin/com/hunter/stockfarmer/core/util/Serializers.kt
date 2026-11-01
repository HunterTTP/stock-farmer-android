package com.hunter.stockfarmer.core.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.util.UUID
import kotlin.math.floor
import kotlin.math.roundToLong

/** A UUID as the upper-case string Swift writes. */
object UuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("Uuid", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: UUID,
    ) = encoder.encodeString(value.toString().uppercase())

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

/** An instant as seconds since 2001-01-01, the number Swift's `Date` writes. */
object InstantSerializer : KSerializer<Instant> {
    private const val REFERENCE_EPOCH = 978_307_200.0

    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.DOUBLE)

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) = encoder.encodeDouble(value.epochSecond.toDouble() - REFERENCE_EPOCH + value.nano / 1_000_000_000.0)

    override fun deserialize(decoder: Decoder): Instant {
        val seconds = decoder.decodeDouble() + REFERENCE_EPOCH
        if (!seconds.isFinite()) throw SerializationException("Invalid date")
        val whole = floor(seconds)
        return Instant.ofEpochSecond(whole.toLong(), ((seconds - whole) * 1_000_000_000).roundToLong())
    }
}
