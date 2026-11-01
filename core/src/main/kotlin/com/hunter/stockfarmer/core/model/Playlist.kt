package com.hunter.stockfarmer.core.model

import java.net.URI
import java.text.Collator
import java.util.Locale

/** Alphabetical by file name, wrapping after the last track. Advance returns an independent value. */
class Playlist private constructor(
    val tracks: List<URI>,
    private val index: Int,
) {
    constructor(tracks: List<URI>) : this(orderTracks(tracks), 0)

    val current: URI? get() = tracks.getOrNull(index)

    fun advance(): Playlist = if (tracks.isEmpty()) this else Playlist(tracks, (index + 1) % tracks.size)

    companion object {
        private val filenameParts = Regex("[0-9]+|[^0-9]+")

        private fun orderTracks(tracks: List<URI>): List<URI> {
            // JVM collation approximates Foundation's locale-specific standard comparison.
            val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
            val ordered =
                tracks.sortedWith { left, right ->
                    compareNames(left.path.substringAfterLast('/'), right.path.substringAfterLast('/'), collator)
                }
            return java.util.List.copyOf(ordered)
        }

        private fun compareNames(
            left: String,
            right: String,
            collator: Collator,
        ): Int {
            val a = filenameParts.findAll(left).map { it.value }.toList()
            val b = filenameParts.findAll(right).map { it.value }.toList()
            for (i in 0 until minOf(a.size, b.size)) {
                val comparison =
                    if (a[i][0] in '0'..'9' && b[i][0] in '0'..'9') {
                        a[i].toBigInteger().compareTo(b[i].toBigInteger())
                    } else {
                        collator.compare(a[i], b[i])
                    }
                if (comparison != 0) return comparison
            }
            return a.size.compareTo(b.size)
        }
    }
}
