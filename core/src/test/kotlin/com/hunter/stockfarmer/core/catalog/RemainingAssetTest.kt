package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.feedback.MusicLoudness
import com.hunter.stockfarmer.core.model.compactDollars
import com.hunter.stockfarmer.core.rules.Season
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest

class RemainingAssetTest {
    private val images =
        listOf(File("../app/src/main/assets/images"), File("app/src/main/assets/images"))
            .first { it.isDirectory }

    private fun digest(
        folder: String,
        name: String,
    ): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(File(images, "$folder/$name.png").readBytes())
            .joinToString("") { "%02x".format(it) }

    @Test fun `new choices do not repeat decoration or working building pictures`() {
        val names =
            Building.all
                .flatMap { building ->
                    listOfNotNull(building.assetName, building.fullAssetName) + building.styles.map { it.assetName }
                }.toSet()
        val buildings = names.map { digest("buildings", it) }.toSet()
        val decorations = mutableSetOf<String>()
        for (decoration in Decoration.shopChoices) {
            for (size in decoration.offeredSizes) {
                val picture = digest(decoration.imageFolder, decoration.assetNames[size])
                assertFalse(picture in buildings, "${decoration.id} repeats a building")
                assertTrue(
                    decorations.add(picture) || decoration.id == Decoration.BIG_SPRINKLER_ID,
                    "${decoration.id} repeats a decoration",
                )
            }
        }
    }

    @Test fun `every new crop has its four bundled growth pictures`() {
        for (crop in Crop.additional) {
            for (phase in 1..4) {
                assertTrue(File(images, "crops/${crop.id}-phase-$phase.png").isFile)
            }
        }
    }

    @Test fun `every track of every season is measured`() {
        for (season in Season.entries) {
            for (track in SeasonalSoundtrack.tracks(season)) {
                val name = File(track).name
                assertTrue(name in MusicLoudness.measured, "$track has no loudness")
            }
        }
    }

    @Test fun `bundled recordings match the seasonal soundtrack`() {
        val music = File(images.parentFile, "audio/Music")
        val bundled =
            music
                .walkTopDown()
                .filter { it.isFile && it.extension == "m4a" }
                .map { it.relativeTo(music).invariantSeparatorsPath.removeSuffix(".m4a") }
                .toSet()
        assertEquals(Season.entries.flatMap { SeasonalSoundtrack.tracks(it) }.toSet(), bundled)
    }

    @Test fun `small amounts are written out`() {
        assertEquals("$25", 25.0.compactDollars)
        assertEquals("$999", 999.0.compactDollars)
    }

    @Test fun `thousands and millions drop their zeros`() {
        for ((amount, text) in listOf(
            14_000.0 to "$14K",
            16_000.0 to "$16K",
            1500.0 to "$1.5K",
            2_000_000.0 to "$2M",
            999_999.0 to "$999.9K",
        )) {
            assertEquals(text, amount.compactDollars)
        }
    }
}
