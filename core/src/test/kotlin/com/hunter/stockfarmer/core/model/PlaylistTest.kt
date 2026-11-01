package com.hunter.stockfarmer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.URI

class PlaylistTest {
    private fun url(name: String): URI = URI("file", null, "/music/$name.m4a", null)

    @Test fun `tracksAreOrderedAlphabeticallyRegardlessOfInputOrder`() {
        val playlist = Playlist(listOf(url("What Clouds"), url("Far Far Away"), url("forgotten Biomes")))
        assertEquals(
            listOf("Far Far Away.m4a", "forgotten Biomes.m4a", "What Clouds.m4a"),
            playlist.tracks.map {
                it.path.substringAfterLast('/')
            },
        )
        assertEquals(url("Far Far Away"), playlist.current)
    }

    @Test fun `advancingWrapsAroundToTheFirstTrack`() {
        var playlist = Playlist(listOf(url("A"), url("B"), url("C")))
        playlist = playlist.advance()
        assertEquals(url("B"), playlist.current)
        playlist = playlist.advance()
        assertEquals(url("C"), playlist.current)
        playlist = playlist.advance()
        assertEquals(url("A"), playlist.current)
    }

    @Test fun `singleTrackRepeatsAndEmptyPlaylistHasNothing`() {
        assertEquals(url("Only"), Playlist(listOf(url("Only"))).advance().current)
        val empty = Playlist(emptyList())
        assertNull(empty.current)
        assertNull(empty.advance().current)
    }

    @Test fun `natural ordering compares numeric runs and copies advance independently`() {
        val tracks = mutableListOf(url("Track 10"), url("Track 2"), url("Track 1"))
        val playlist = Playlist(tracks)
        tracks.clear()
        assertEquals(listOf(url("Track 1"), url("Track 2"), url("Track 10")), playlist.tracks)
        assertEquals(url("Track 2"), playlist.advance().current)
        assertEquals(url("Track 1"), playlist.current)
    }
}
