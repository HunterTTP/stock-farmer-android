package com.hunter.stockfarmer.core.persistence

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlin.math.floor
import kotlin.math.roundToLong

@Serializable
data class TimelapseFrame(
    @Serializable(with = TimelapseUUIDSerializer::class) val id: UUID,
    val day: Int,
    val minute: Int,
    @Serializable(with = TimelapseDateSerializer::class) val capturedAt: Instant,
    val bytes: Int,
)

@Serializable
@ConsistentCopyVisibility
data class TimelapseArchive private constructor(
    @Serializable(with = TimelapseUUIDSerializer::class) val generation: UUID,
    val lastCapturedDay: Int?,
    @SerialName("frames") private val savedFrames: List<TimelapseFrame>,
) {
    constructor(generation: UUID, lastCapturedDay: Int? = null) : this(generation, lastCapturedDay, emptyList())

    val frames: List<TimelapseFrame> get() = savedFrames.toList()
    val totalBytes: Long get() = savedFrames.sumOf { it.bytes.toLong() }

    internal fun appending(frame: TimelapseFrame): TimelapseArchive =
        copy(lastCapturedDay = frame.day, savedFrames = savedFrames + frame)
}

/** Serial image storage outside game saves. A generation rejects captures pending when history was cleared. */
class TimelapseStore(
    private val directory: Path,
    private val files: SaveFileSystem = LocalSaveFileSystem(),
    private val newID: () -> UUID = UUID::randomUUID,
) {
    @Synchronized
    fun archive(farm: UUID): TimelapseArchive {
        val data = files.read(indexURL(farm))
        if (data != null) {
            val archive = json.decodeFromString<TimelapseArchive>(data.toString(Charsets.UTF_8))
            val frames = archive.frames
            if (!frames.all { it.day > 0 && it.minute in 0 until 1440 && it.bytes in 1..100_000_000 } ||
                frames.map { it.id }.toSet().size != frames.size
            ) {
                throw IOException("Corrupt timelapse archive")
            }
            return archive
        }
        val archive = TimelapseArchive(newID())
        write(archive, farm)
        return archive
    }

    @Synchronized
    fun append(
        data: ByteArray,
        farm: UUID,
        generation: UUID,
        day: Int,
        minute: Int,
        at: Instant,
    ): TimelapseArchive? {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Timelapse capture cancelled")
        val archive = archive(farm)
        if (archive.generation != generation) return null
        if (archive.lastCapturedDay == day || archive.frames.any { it.day == day }) return archive
        if (data.isEmpty() || data.size > 100_000_000 || day <= 0 || minute !in 0 until 1440) {
            throw IOException("Invalid timelapse capture")
        }
        val frame = TimelapseFrame(newID(), day, minute, at, data.size)
        val url = imageURL(frame, farm)
        files.write(data, url)
        val appended = archive.appending(frame)
        try {
            write(appended, farm)
        } catch (error: Exception) {
            try {
                files.remove(url)
            } catch (_: Exception) {
                // Preserve the index-write error even if image cleanup also fails.
            }
            throw error
        }
        return appended
    }

    @Synchronized
    fun image(
        frame: TimelapseFrame,
        farm: UUID,
    ): ByteArray? = files.read(imageURL(frame, farm))

    @Synchronized
    fun clear(farm: UUID): TimelapseArchive {
        val capturedDay =
            try {
                archive(farm).lastCapturedDay
            } catch (_: Exception) {
                null
            }
        // Remove the whole folder, including unindexed images left by an interrupted write.
        files.remove(folder(farm))
        val empty = TimelapseArchive(newID(), capturedDay)
        write(empty, farm)
        return empty
    }

    @Synchronized
    fun remove(farm: UUID) = files.remove(folder(farm))

    private fun folder(farm: UUID): Path = directory.resolve(farm.toString().uppercase())

    private fun indexURL(farm: UUID): Path = folder(farm).resolve("index.json")

    private fun imageURL(
        frame: TimelapseFrame,
        farm: UUID,
    ): Path = folder(farm).resolve(frame.id.toString().uppercase() + ".png")

    private fun write(
        archive: TimelapseArchive,
        farm: UUID,
    ) {
        files.createDirectory(folder(farm))
        files.write(json.encodeToString(archive).toByteArray(Charsets.UTF_8), indexURL(farm))
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}

internal object TimelapseUUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("TimelapseUUID", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: UUID,
    ) = encoder.encodeString(value.toString().uppercase())

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

/** Foundation's default Date encoding counts seconds from 2001-01-01. */
internal object TimelapseDateSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("TimelapseDate", PrimitiveKind.DOUBLE)

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) = encoder.encodeDouble(value.epochSecond.toDouble() - referenceEpoch + value.nano / 1_000_000_000.0)

    override fun deserialize(decoder: Decoder): Instant {
        val seconds = decoder.decodeDouble() + referenceEpoch
        if (!seconds.isFinite()) throw SerializationException("Invalid capture date")
        val whole = floor(seconds)
        return Instant.ofEpochSecond(whole.toLong(), ((seconds - whole) * 1_000_000_000).roundToLong())
    }

    private val referenceEpoch = 978_307_200.0
}
