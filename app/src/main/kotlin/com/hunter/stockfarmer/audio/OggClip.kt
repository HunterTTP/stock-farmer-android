package com.hunter.stockfarmer.audio

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads an Ogg Vorbis file's length from its headers and last page without decoding samples. */
internal data class OggClip(
    val sampleRate: Long,
    val channels: Int,
    val frames: Long,
) {
    val duration: Double get() = frames.toDouble() / sampleRate

    /** What SoundPool holds once the clip is decoded to 16-bit samples. */
    val byteCount: Long get() = frames * channels * 2

    companion object {
        private const val IDENTIFICATION_HEADER = 30
        private const val GRANULE_OFFSET = 6
        private val PAGE_MARK = "OggS".toByteArray(Charsets.US_ASCII)

        fun read(input: InputStream): OggClip = read(input.readBytes())

        fun read(bytes: ByteArray): OggClip {
            require(bytes.size > 27 + IDENTIFICATION_HEADER && matches(bytes, 0)) { "Expected Ogg" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val packet = 27 + (bytes[26].toInt() and 0xff)
            require(bytes[packet].toInt() == 1 && String(bytes, packet + 1, 6, Charsets.US_ASCII) == "vorbis") {
                "Expected Vorbis"
            }
            val channels = bytes[packet + 11].toInt() and 0xff
            val sampleRate = buffer.getInt(packet + 12).toLong() and 0xffffffffL
            require(channels > 0 && sampleRate > 0)
            val lastPage = (bytes.size - 27 downTo 0).firstOrNull { matches(bytes, it) }
            val frames = lastPage?.let { buffer.getLong(it + GRANULE_OFFSET) } ?: 0L
            require(frames > 0) { "Ogg has no samples" }
            return OggClip(sampleRate, channels, frames)
        }

        private fun matches(
            bytes: ByteArray,
            offset: Int,
        ): Boolean = PAGE_MARK.indices.all { bytes[offset + it] == PAGE_MARK[it] }
    }
}
