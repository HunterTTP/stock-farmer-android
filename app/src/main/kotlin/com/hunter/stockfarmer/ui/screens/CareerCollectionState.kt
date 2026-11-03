package com.hunter.stockfarmer.ui.screens

import androidx.compose.ui.graphics.Color
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.Dog
import com.hunter.stockfarmer.core.catalog.DogBreed
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.rules.Butterflies
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.ToolUpgrade
import com.hunter.stockfarmer.core.rules.hasSeenEnvironment
import com.hunter.stockfarmer.core.rules.level
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.hudColor
import java.util.Locale

internal enum class CareerCollection(
    val title: String,
) {
    CROPS("Crops"),
    FISH("Fish"),
    ANIMALS("Animals"),
    BUILDINGS("Buildings"),
    DECORATIONS("Decorations"),
    TERRAIN("Terrain"),
    RECIPES("Recipes"),
    ENVIRONMENT("Environment"),
    TOOLS("Tools"),
}

internal enum class CareerFilter(
    val title: String,
) {
    ALL("All"),
    COLLECTED("Collected"),
    LOCKED("Not yet"),
}

internal data class CareerEntry(
    val id: String,
    val name: String,
    val collected: Boolean,
    val detail: String? = null,
    val art: ScreenArt? = null,
    val symbol: String = "✦",
    /** The name's color once collected, where it is not the usual white. */
    val nameColor: Color? = null,
)

internal data class CareerSection(
    val title: String,
    val entries: List<CareerEntry>,
    val tint: Color? = null,
) {
    val collected: Int get() = entries.count { it.collected }

    fun filtered(
        query: String,
        filter: CareerFilter,
    ): CareerSection? {
        val words = query.split(' ').filter { it.isNotEmpty() }
        val kept =
            entries.filter { entry ->
                val matchesFilter =
                    when (filter) {
                        CareerFilter.ALL -> true
                        CareerFilter.COLLECTED -> entry.collected
                        CareerFilter.LOCKED -> !entry.collected
                    }
                matchesFilter && words.all { entry.name.contains(it, true) || title.contains(it, true) }
            }
        return if (kept.isEmpty()) null else copy(entries = kept)
    }
}

internal object CareerCollections {
    fun sections(
        engine: GameEngine,
        collection: CareerCollection,
    ): List<CareerSection> {
        val record = engine.state.career
        return when (collection) {
            CareerCollection.CROPS -> {
                listOf(crops(record))
            }

            CareerCollection.FISH -> {
                fish(record)
            }

            CareerCollection.ANIMALS -> {
                animals(record)
            }

            CareerCollection.BUILDINGS -> {
                buildings(record)
            }

            CareerCollection.DECORATIONS -> {
                listOf(decorations(record))
            }

            CareerCollection.TERRAIN -> {
                terrain(record)
            }

            CareerCollection.RECIPES -> {
                Building.all.mapNotNull { building ->
                    val entries =
                        Recipe.options(building).map {
                            CareerEntry(
                                it.id,
                                it.output.name,
                                it.id in record.madeRecipeIDs,
                                art = ScreenArt.item(it.output),
                                symbol = it.output.iconSymbol,
                            )
                        }
                    if (entries.isEmpty()) null else CareerSection(building.name, entries)
                }
            }

            CareerCollection.ENVIRONMENT -> {
                environment(engine)
            }

            CareerCollection.TOOLS -> {
                HarvestTool.entries.map { tool ->
                    val levels =
                        listOf(1) +
                            ToolUpgrade.all
                                .filter { it.tool == tool }
                                .map { it.level }
                                .sorted()
                    val entries =
                        levels.map {
                            CareerEntry(
                                "${tool.rawValue}-$it",
                                "Level $it",
                                engine.level(tool) >= it,
                                art = ScreenArt.image("tools", tool.imageName),
                                nameColor = Theme.toolLevel(it),
                            )
                        }
                    CareerSection(tool.displayName, entries)
                }
            }
        }
    }

    private fun crops(record: CareerRecord): CareerSection {
        val entries =
            Crop.all.map {
                CareerEntry(it.id, it.name, it.id in record.plantedCropIDs, art = ScreenArt.crop(it))
            }
        return CareerSection("Crops", entries)
    }

    private fun fish(record: CareerRecord): List<CareerSection> =
        Rarity.entries.map { rarity ->
            val entries =
                FishKind.all.filter { it.rarity == rarity }.map {
                    val weight = record.heaviestCatches[it.id]
                    val detail =
                        weight?.let { best ->
                            if (best > 0) String.format(Locale.US, "Best %.1f lb", best) else "Caught"
                        }
                    CareerEntry(it.id, it.name, weight != null, detail, ScreenArt.image("animals/Fish", it.assetName))
                }
            CareerSection(rarity.displayName, entries, rarity.hudColor)
        }

    private fun buildings(record: CareerRecord): List<CareerSection> {
        val buildings =
            Building.all.map {
                CareerEntry(
                    it.id,
                    it.name,
                    it.id in record.builtBuildingIDs,
                    art = ScreenArt.image("buildings", it.assetName),
                )
            }
        val upgrades =
            HomeUpgrade.buildingIDs.flatMap { id ->
                val building = Building.named(id) ?: return@flatMap emptyList()
                HomeUpgrade.all.filter { it.buildingID == id }.sortedBy { it.level }.map {
                    CareerEntry(
                        it.careerID,
                        "${building.name} Level ${it.level}",
                        it.careerID in record.reachedHomeLevels,
                        art = ScreenArt.image("buildings", building.assetName),
                    )
                }
            }
        return listOf(CareerSection("Buildings", buildings), CareerSection("Building Upgrades", upgrades))
    }

    private fun decorations(record: CareerRecord): CareerSection {
        val entries =
            Decoration.all.filter { it.offeredSizes.isNotEmpty() }.map {
                CareerEntry(
                    it.id,
                    it.name,
                    it.id in record.placedDecorationIDs,
                    art = ScreenArt.image(it.imageFolder, it.assetNames[it.offeredSizes.last()]),
                )
            }
        return CareerSection("Decorations", entries)
    }

    private fun animals(record: CareerRecord): List<CareerSection> {
        val chickens =
            ChickenVariant.available.map {
                val art =
                    ScreenArt("images/animals/Chickens/${it.assetName}.png", frameWidth = 48, rows = 4, column = 3)
                CareerEntry(it.id, it.name, it.id in record.workedAnimalIDs, art = art)
            }
        val livestock =
            LivestockSpecies.all.map { species ->
                val entries =
                    species.variants.map {
                        val width = if (species.id in listOf("cow", "donkey")) 144 else 96
                        val art =
                            ScreenArt(
                                "images/animals/${species.folder}/${it.assetName}.png",
                                frameWidth = width,
                                rows = if (it.hasShearedPictures) 8 else 4,
                            )
                        CareerEntry(it.id, it.name, it.id in record.workedAnimalIDs, art = art)
                    }
                CareerSection(species.pluralName, entries)
            }
        val pets =
            Cat.all.map {
                val art =
                    ScreenArt("images/animals/Cats/${it.assetName}.png", frameWidth = 32, frameHeight = 32, row = 1)
                CareerEntry("cat-${it.id}", it.name, it.id in record.workedAnimalIDs, art = art)
            } +
                Dog.all.map {
                    val art =
                        ScreenArt(
                            "images/animals/Dogs/${it.assetName}.png",
                            columns = 24,
                            rows = if (it.breed == DogBreed.basenji) 15 else 13,
                            row = 2,
                        )
                    CareerEntry(it.id, it.name, it.id in record.workedAnimalIDs, art = art)
                }
        return listOf(CareerSection("Chickens", chickens)) + livestock + CareerSection("Pets", pets)
    }

    private fun terrain(record: CareerRecord): List<CareerSection> {
        fun tile(
            id: String,
            name: String,
            art: ScreenArt? = null,
            symbol: String = "▧",
        ) = CareerEntry(id, name, id in record.placedTerrainIDs, art = art, symbol = symbol)
        val baseTiles =
            listOf(
                tile("grass", "Grass", ScreenArt("images/terrain/grass.jpg")),
                tile("farmland", "Farmland", ScreenArt("images/terrain/farmland.jpg")),
            )
        val water = WaterStyle.entries.map { tile(CareerRecord.terrainID(it), "${it.displayName} Water", symbol = "≈") }
        val covers = GroundCover.entries.map { tile(it.rawValue, it.displayName, symbol = "▧") }
        val tiles = baseTiles + water + covers
        val trees =
            TreeSpecies.all.map { species ->
                val entries =
                    species.colors.flatMap { color ->
                        species.trees.map { tree ->
                            tile(
                                CareerRecord.terrainID(tree, color),
                                "${tree.size.displayName} ${color.name}",
                                ScreenArt.image("trees", tree.assetName(color)),
                            )
                        }
                    }
                CareerSection("${species.name} Trees", entries)
            }

        fun rocks(
            title: String,
            sizes: List<RockSize>,
        ) = CareerSection(
            title,
            sizes.map {
                CareerEntry(
                    it.rawValue,
                    it.displayName,
                    it.rawValue in record.minedRockIDs,
                    art = ScreenArt.image("rocks", it.assetName),
                )
            },
        )
        return listOf(CareerSection("Tiles", tiles)) + trees +
            listOf(
                rocks("Rocks", RockSize.stoneSizes),
                rocks("Ore Veins", RockSize.oreSizes),
            )
    }

    private fun environment(engine: GameEngine): List<CareerSection> {
        fun sight(
            id: String,
            name: String,
            symbol: String,
        ) = CareerEntry(id, name, engine.hasSeenEnvironment(id), symbol = symbol)
        return listOf(
            CareerSection(
                "Seasons",
                Season.entries.map {
                    sight(CareerRecord.environmentID(it), it.name.replaceFirstChar(Char::uppercaseChar), "❋")
                },
            ),
            CareerSection(
                "Butterflies",
                Butterflies.Wings.all.map {
                    val name =
                        when (it) {
                            is Butterflies.Wings.Color -> "${it.rarity.displayName} Wings"
                            Butterflies.Wings.Shifting -> "Shifting Wings"
                        }
                    sight(CareerRecord.environmentID(it), name, "🦋")
                },
            ),
            CareerSection("Night", listOf(sight(CareerRecord.FIREFLY_ID, "Fireflies", "✧"))),
            CareerSection(
                "Weather",
                listOf(
                    sight(CareerRecord.RAIN_ID, "Rain", "☂"),
                    sight(CareerRecord.SNOW_ID, "Snow", "❄"),
                ),
            ),
        )
    }
}
