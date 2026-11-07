package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.catalog.ArtisanGood
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.Dog
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.FencePiece
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.rules.FruitTreePhase
import com.hunter.stockfarmer.core.rules.GrowthPhase
import com.hunter.stockfarmer.core.rules.Season
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Validates that every asset path resolved by FarmRenderer, TextureLibrary, TractorLayer,
 * AnimalSprites, FishSource, and effect layers exists on disk and is a decodable image.
 * This guarantees libGDX cannot crash due to missing textures during gameplay.
 */
class FarmAssetCoverageTest {
    private val assetsRoot: File =
        listOf(
            File("src/main/assets/images"),
            File("app/src/main/assets/images"),
        ).firstOrNull { it.isDirectory }
            ?: error("Could not locate assets/images directory")

    private fun resolveAsset(
        folder: String,
        name: String,
        extension: String = "png",
        season: Season = Season.spring,
    ): File {
        val seasonal =
            if (season == Season.spring) {
                null
            } else {
                File(assetsRoot, "seasons/${season.name}/$folder/$name.$extension")
            }
        val defaultFile = File(assetsRoot, "$folder/$name.$extension")
        return if (seasonal != null && seasonal.isFile) seasonal else defaultFile
    }

    private fun assertImageFile(
        file: File,
        label: String = file.path,
    ) {
        assertTrue(file.isFile, "Missing asset ($label): ${file.path}")
        assertTrue(file.length() > 0, "Empty asset file: ${file.path}")
        val image = ImageIO.read(file)
        assertTrue(image != null && image.width > 0 && image.height > 0, "Corrupt image asset: ${file.path}")
    }

    @Test
    fun `every ground terrain asset and seasonal variation exists`() {
        val baseGrass = File(assetsRoot, "terrain/grass.jpg")
        val farmland = File(assetsRoot, "terrain/farmland.jpg")
        val saturated = File(assetsRoot, "terrain/farmland_saturated.jpg")

        assertImageFile(baseGrass, "grass")
        assertImageFile(farmland, "farmland")
        assertImageFile(saturated, "farmland_saturated")

        for (season in listOf(Season.fall, Season.winter)) {
            val seasonalGrass = File(assetsRoot, "seasons/${season.name}/terrain/grass.jpg")
            assertImageFile(seasonalGrass, "grass ($season)")
        }

        for (cover in GroundCover.entries) {
            assertTrue(cover.assetName.isNotEmpty())
        }
    }

    @Test
    fun `every catalog building asset and all styled variants exist across all seasons`() {
        val buildingNames =
            buildSet {
                for (building in Building.all) {
                    add(building.assetName)
                    for (style in building.styles) {
                        add(style.assetName)
                    }
                    building.fullAssetName?.let { add(it) }
                }
            }
        assertTrue(buildingNames.size >= 30, "Found ${buildingNames.size} building assets")

        for (name in buildingNames) {
            val baseFile = File(assetsRoot, "buildings/$name.png")
            assertImageFile(baseFile, "building: $name")

            for (season in Season.entries) {
                val resolved = resolveAsset("buildings", name, season = season)
                assertImageFile(resolved, "building: $name ($season)")
            }
        }
    }

    @Test
    fun `every crop and fruit tree asset exists across all phases and seasons`() {
        for (crop in Crop.all) {
            if (crop.isFruitTree) {
                for (phase in FruitTreePhase.entries) {
                    val asset = crop.treeAssetName(phase)
                    for (season in Season.entries) {
                        val resolved = resolveAsset("crops/fruit_trees", asset, season = season)
                        assertImageFile(resolved, "fruit tree: ${crop.id} $phase ($season)")
                    }
                }
                val basket = File(assetsRoot, "crops/fruit_trees/${crop.basketAssetName}.png")
                assertImageFile(basket, "basket: ${crop.id}")
            } else {
                for (phase in listOf(GrowthPhase.sprout, GrowthPhase.growing, GrowthPhase.ready)) {
                    val asset = "${crop.id}-phase-${phase.spriteNumber}"
                    val file = File(assetsRoot, "crops/$asset.png")
                    assertImageFile(file, "crop: ${crop.id} phase ${phase.spriteNumber}")
                }
            }
        }
    }

    @Test
    fun `every tree species size and color exists across all seasons`() {
        var count = 0
        for (species in TreeSpecies.all) {
            for (tree in species.trees) {
                for (color in species.colors) {
                    val asset = tree.assetName(color)
                    val base = File(assetsRoot, "trees/$asset.png")
                    assertImageFile(base, "tree: $asset")
                    count++

                    for (season in Season.entries) {
                        val resolved = resolveAsset("trees", asset, season = season)
                        assertImageFile(resolved, "tree: $asset ($season)")
                    }
                }
            }
        }
        assertTrue(count >= 30, "Found $count tree configurations")
        assertImageFile(File(assetsRoot, "trees/log.png"), "log")
    }

    @Test
    fun `every rock size asset exists across all seasons`() {
        for (size in RockSize.entries) {
            val asset = size.assetName
            val base = File(assetsRoot, "rocks/$asset.png")
            assertImageFile(base, "rock: $asset")

            for (season in Season.entries) {
                val resolved = resolveAsset("rocks", asset, season = season)
                assertImageFile(resolved, "rock: $asset ($season)")
            }
        }
    }

    @Test
    fun `every decoration and variant size exists across all seasons`() {
        var count = 0
        for (decoration in Decoration.all) {
            for (asset in decoration.assetNames) {
                val base = File(assetsRoot, "${decoration.imageFolder}/$asset.png")
                assertImageFile(base, "decoration: $asset")
                count++

                for (season in Season.entries) {
                    val resolved = resolveAsset(decoration.imageFolder, asset, season = season)
                    assertImageFile(resolved, "decoration: $asset ($season)")
                }
            }
        }
        assertTrue(count >= 15, "Found $count decoration assets")
    }

    @Test
    fun `every fence style color and piece exists across all seasons`() {
        for (color in FenceColor.all) {
            for (piece in FencePiece.entries) {
                val asset = "fence-${piece.rawValue}"
                val folder = "decorations/${color.folder}"
                val base = File(assetsRoot, "$folder/$asset.png")
                assertImageFile(base, "fence: ${color.id} $asset")

                for (season in Season.entries) {
                    val resolved = resolveAsset(folder, asset, season = season)
                    assertImageFile(resolved, "fence: ${color.id} $asset ($season)")
                }
            }
        }
    }

    @Test
    fun `every animal pet chicken and livestock sprite sheet exists`() {
        for (cat in Cat.all) {
            assertImageFile(File(assetsRoot, "animals/Cats/${cat.assetName}.png"), "cat: ${cat.id}")
        }
        for (dog in Dog.all) {
            assertImageFile(File(assetsRoot, "animals/Dogs/${dog.assetName}.png"), "dog: ${dog.id}")
        }
        assertImageFile(
            File(assetsRoot, "animals/Dogs/Dogs_Doghouse_Sleeping_48x48.png"),
            "doghouse sleeping",
        )
        for (variant in ChickenVariant.available) {
            assertImageFile(
                File(assetsRoot, "animals/Chickens/${variant.assetName}.png"),
                "chicken: ${variant.id}",
            )
        }
        assertImageFile(File(assetsRoot, "animals/Chickens/Chick_48x48.png"), "chick")
        assertImageFile(File(assetsRoot, "animals/Chickens/Egg_Big_48x48.png"), "egg")

        for (species in LivestockSpecies.all) {
            for (variant in species.variants) {
                assertImageFile(
                    File(assetsRoot, "animals/${species.folder}/${variant.assetName}.png"),
                    "livestock adult: ${species.id} ${variant.id}",
                )
                assertImageFile(
                    File(assetsRoot, "animals/${species.folder}/${variant.babyAssetName}.png"),
                    "livestock baby: ${species.id} ${variant.id}",
                )
            }
        }
        assertImageFile(File(assetsRoot, "animals/Cows/Milk_Can_2_48x48.png"), "milk can")
    }

    @Test
    fun `every fish kind exists`() {
        for (kind in FishKind.all) {
            assertImageFile(File(assetsRoot, "animals/Fish/${kind.assetName}.png"), "fish: ${kind.id}")
        }
    }

    @Test
    fun `tractor and vehicle assets exist`() {
        assertImageFile(File(assetsRoot, "vehicles/Tractor_48x48.png"), "tractor")
    }

    @Test
    fun `effect background and motion assets exist`() {
        assertImageFile(File(assetsRoot, "backgrounds/space.jpg"), "space background")

        for (color in listOf("orange", "red", "yellow")) {
            assertImageFile(File(assetsRoot, "seasons/fall/leaves/leaf_$color.png"), "fall leaf $color")
        }

        val motionSheets =
            listOf(
                "siloDoor",
                "dairyWork",
                "ovenSmoke",
                "well",
                "chest1",
                "chest2",
            )
        for (sheet in motionSheets) {
            assertImageFile(File(assetsRoot, "motion/$sheet.png"), "motion: $sheet")
        }
    }

    @Test
    fun `tool and product assets exist`() {
        assertImageFile(File(assetsRoot, "tools/fertilizer.png"), "fertilizer tool")
        for (tool in HarvestTool.entries) {
            val asset =
                when (tool) {
                    HarvestTool.scythe -> "harvester"
                    HarvestTool.axe -> "axe"
                    HarvestTool.pickaxe -> "pickaxe"
                    HarvestTool.bucket -> "bucket"
                    HarvestTool.rod -> "rod"
                }
            assertImageFile(File(assetsRoot, "tools/$asset.png"), "tool: $tool")
        }

        assertImageFile(File(assetsRoot, "products/Cheese_1_48x48.png"), "cheese product")
        for (good in ArtisanGood.entries) {
            assertImageFile(File(assetsRoot, "products/${good.assetName}.png"), "artisan good: ${good.rawValue}")
        }
    }
}
