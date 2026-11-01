package com.hunter.stockfarmer.audio

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads RIFF chunks without decoding samples or retaining the recording in memory. */
internal data class WaveClip(
    val sampleRate: Long,
    val byteCount: Long,
) {
    val duration: Double get() = byteCount.toDouble() / (sampleRate * 2)

    companion object {
        fun read(input: InputStream): WaveClip {
            val stream = DataInputStream(input)
            val header = ByteArray(12).also(stream::readFully)
            require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF")
            require(String(header, 8, 4, Charsets.US_ASCII) == "WAVE")
            var remaining = unsignedInt(header, 4) - 4
            var sampleRate: Long? = null
            var dataBytes: Long? = null
            while (remaining >= 8) {
                val chunk = ByteArray(8).also(stream::readFully)
                val size = unsignedInt(chunk, 4)
                val paddedSize = size + size % 2
                require(paddedSize <= remaining - 8)
                when (String(chunk, 0, 4, Charsets.US_ASCII)) {
                    "fmt " -> {
                        require(size >= 16)
                        val format =
                            ByteBuffer
                                .wrap(ByteArray(16).also(stream::readFully))
                                .order(ByteOrder.LITTLE_ENDIAN)
                        require(format.getShort(0).toInt() == 1) { "Expected PCM WAV" }
                        require(format.getShort(2).toInt() == 1) { "Expected mono WAV" }
                        require(format.getShort(14).toInt() == 16) { "Expected 16-bit WAV" }
                        sampleRate = format.getInt(4).toLong() and 0xffffffffL
                        require(sampleRate > 0)
                        require(format.getShort(12).toInt() == 2)
                        require((format.getInt(8).toLong() and 0xffffffffL) == sampleRate * 2)
                        skip(stream, paddedSize - 16)
                    }

                    "data" -> {
                        dataBytes = size
                        skip(stream, paddedSize)
                    }

                    else -> {
                        skip(stream, paddedSize)
                    }
                }
                remaining -= 8 + paddedSize
            }
            require(sampleRate != null && dataBytes != null && dataBytes > 0 && dataBytes % 2 == 0L)
            return WaveClip(sampleRate, dataBytes)
        }

        private fun unsignedInt(
            bytes: ByteArray,
            offset: Int,
        ): Long =
            ByteBuffer
                .wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt(offset)
                .toLong() and 0xffffffffL

        private fun skip(
            stream: InputStream,
            count: Long,
        ) {
            var remaining = count
            while (remaining > 0) {
                val skipped = stream.skip(remaining)
                if (skipped > 0) {
                    remaining -= skipped
                } else {
                    if (stream.read() == -1) throw IOException("Truncated WAV chunk")
                    remaining--
                }
            }
        }
    }
}
