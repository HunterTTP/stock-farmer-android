package com.hunter.stockfarmer.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The bundled recordings every sound refers to: they must exist and be in the one format the audio engine plays them in,
 * since a clip recorded differently is left out of the game without a word.
 */
class SoundAssetTest {
    private val effects = File("../app/src/main/assets/audio/Effects")

    private data class Format(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
    )

    private fun files(cue: SoundCue): List<File> =
        File(effects, cue.clips.folder)
            .listFiles { file -> file.extension == "wav" && cue.clips.includes(file.nameWithoutExtension) }
            .orEmpty()
            .sortedBy { it.name }

    /** The format a WAV file declares in its `fmt ` chunk, which follows the 12-byte RIFF header. */
    private fun format(file: File): Format {
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes.array(), 0, 4, Charsets.ISO_8859_1))
        assertEquals("fmt ", String(bytes.array(), 12, 4, Charsets.ISO_8859_1))
        return Format(
            sampleRate = bytes.getInt(24),
            channels = bytes.getShort(22).toInt(),
            bitsPerSample = bytes.getShort(34).toInt(),
        )
    }

    @Test
    fun `every sound has recordings`() {
        for (cue in SoundCue.entries) assertFalse(files(cue).isEmpty(), "$cue has no recordings")
    }

    @Test
    fun `every recording is in the same format`() {
        val all = SoundCue.entries.flatMap(::files)
        val first = format(all.first())
        for (file in all) assertEquals(first, format(file), "${file.name} is recorded differently")
        assertEquals(16, first.bitsPerSample)
        // One channel, so that a sound can be placed to the left or right of the player.
        assertEquals(1, first.channels)
    }
}
