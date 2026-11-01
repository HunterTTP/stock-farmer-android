package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.feedback.SoundCue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AudioAssetsTest {
    private val assets = File("src/main/assets")

    @Test fun `every cue has playable OGG variants within the SoundPool decoded limit`() {
        for (cue in SoundCue.entries) {
            val files =
                File(assets, "audio/Effects/${cue.clips.folder}")
                    .listFiles()
                    .orEmpty()
                    .filter { it.extension == "ogg" && cue.clips.includes(it.nameWithoutExtension) }
            assertTrue(files.isNotEmpty(), cue.name)
            for (file in files) {
                val clip = file.inputStream().use(OggClip::read)
                assertTrue(clip.sampleRate in setOf(44100L, 48000L), file.name)
                assertTrue(clip.duration > 0, file.name)
                assertTrue(clip.byteCount <= 1_048_576, "${file.name} exceeds SoundPool's decoded buffer")
            }
        }
    }

    @Test fun `seasonal playlists resolve all twenty assets including unicode and spaces`() {
        val playlists = MusicPlayer.playlists { File(assets, it).isFile }
        val tracks = playlists.values.flatMap { it.tracks }
        assertEquals(20, tracks.size)
        assertEquals(20, tracks.toSet().size)
        for (track in tracks) assertTrue(File(assets, track.path.removePrefix("/")).isFile)
        assertTrue(tracks.any { it.path.contains("Gymnopédie") })
    }
}
