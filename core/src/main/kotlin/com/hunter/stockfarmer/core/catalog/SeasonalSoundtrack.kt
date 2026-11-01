package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.rules.Season

/** Bundled recordings assigned once across the seasons, with the original soundtrack kept in Spring. */
object SeasonalSoundtrack {
    /** Resource paths relative to Audio/Music, without the extension. */
    fun tracks(season: Season): List<String> =
        when (season) {
            Season.spring -> {
                listOf(
                    "Far Far Away",
                    "Forgotten Biomes",
                    "What Clouds Are Made Of",
                    "expanded/Chickens In The Meadow",
                    "expanded/Ducks In A Pond",
                    "expanded/Sheep",
                    "expanded/Wildflowers By The River",
                )
            }

            Season.fall -> {
                listOf(
                    "fall/Sunlight Through Leaves",
                    "expanded/Drifting Memories",
                    "expanded/Evening Harmony",
                    "expanded/Wanderer's Tale",
                    "expanded/Gentle Breeze",
                )
            }

            Season.winter -> {
                listOf(
                    "winter/Polar Lights",
                    "winter/Wind Over The Trees",
                    "expanded/Floating Dream",
                    "expanded/Strange Worlds",
                    "expanded/Whispering Woods",
                    "classical/Gymnopédie No.1",
                    "expanded/Pineapple Under The Sea",
                    "classical/Minuet",
                )
            }
        }
}
