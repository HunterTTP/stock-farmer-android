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
            .listFiles { file ->
                (file.extension == "ogg" || file.extension == "wav") &&
                    cue.clips.includes(file.nameWithoutExtension)
            }.orEmpty()
            .sortedBy { it.name }

    /** The format an Ogg Vorbis file declares in its OggS container header. */
    private fun format(file: File): String {
        val bytes = file.readBytes()
        val magic = String(bytes, 0, 4, Charsets.ISO_8859_1)
        assertEquals("OggS", magic)
        return magic
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
    }
}
