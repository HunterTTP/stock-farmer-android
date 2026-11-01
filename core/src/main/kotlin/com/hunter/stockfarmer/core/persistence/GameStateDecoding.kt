package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.XPSample
import com.hunter.stockfarmer.core.rules.ToolUpgrade
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

private val retiredFences =
    setOf(
        "darkWood",
        "rustic",
        "picket",
        "lightPicket",
        "goldenPicket",
        "redPicket",
        "whiteRail",
        "brownRail",
        "darkRail",
        "woodGate",
        "darkWoodGate",
        "rusticGate",
    )

/** Applies the remaining Swift save rules to Android's own JSON representation. */
internal fun JsonObject.prepareGameState(): JsonObject {
    val fields = toMutableMap()
    for (key in listOf("hasHarvested", "openGates", "decoratesWithTrees", "axeLevel")) fields.remove(key)
    if (fields["harvestTool"]?.jsonPrimitive?.content in listOf("saw", "tractor")) {
        fields["harvestTool"] = JsonPrimitive("scythe")
    }
    (fields["sellingTools"] as? JsonArray)?.let { tools ->
        fields["sellingTools"] = JsonArray(tools.filter { it.jsonPrimitive.content != "tractor" })
    }
    if (fields["selectedFenceColorID"]?.jsonPrimitive?.content in retiredFences) {
        fields["selectedFenceColorID"] = JsonPrimitive("wood")
    }
    fields.mapValues("fences") { value ->
        if (value.jsonPrimitive.content in retiredFences) JsonPrimitive("wood") else value
    }
    fields.mapValues("herds") { value -> JsonObject((value as JsonObject) - "speciesID") }
    val barns =
        (this["herds"] as? JsonArray)
            ?.chunked(2)
            ?.associate { pair ->
                pair[0] to (pair[1] as JsonObject)["speciesID"]?.jsonPrimitive?.contentOrNull
            }.orEmpty()
    fields.mapValues("buildings") { value, key ->
        if (value.jsonPrimitive.content == "barn1") {
            when (barns[key]) {
                "goat" -> JsonPrimitive("goatBarn")
                "pig" -> JsonPrimitive("pigBarn")
                else -> value
            }
        } else {
            value
        }
    }
    fields.mapValues("groundCover") { value -> if (value.jsonPrimitive.content == "raisedBed") null else value }
    fields.mapValues("decorations") { value ->
        if ((value as JsonObject)["decorationID"]?.jsonPrimitive?.content == "goldenNest") null else value
    }
    val retiredNests =
        (this["decorations"] as? JsonArray)?.chunked(2)?.count {
            (it[1] as JsonObject)["decorationID"]?.jsonPrimitive?.content == "goldenNest"
        } ?: 0
    if (retiredNests > 0) {
        val money = (fields["money"] as? JsonPrimitive)?.content?.toDouble() ?: 0.0
        if (money in 0.0..GameState.MAX_MONEY) {
            fields["money"] = JsonPrimitive(minOf(GameState.MAX_MONEY, money + retiredNests * 750.0))
        }
    }
    (fields["eggs"] as? JsonArray)?.let { eggs ->
        fields["eggs"] = JsonArray(eggs.map { JsonObject((it as JsonObject) - "isGolden") })
    }
    val axeLevel = this["axeLevel"]
    if (axeLevel != null) {
        val levels = (fields["toolLevels"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        levels.putIfAbsent("axe", JsonPrimitive(axeLevel.jsonPrimitive.int.coerceIn(1, ToolUpgrade.maxLevel)))
        fields["toolLevels"] = JsonObject(levels)
    }
    return JsonObject(fields)
}

private fun MutableMap<String, JsonElement>.mapValues(
    key: String,
    transform: (JsonElement, JsonElement) -> JsonElement?,
) {
    val pairs = this[key] as? JsonArray ?: return
    this[key] =
        JsonArray(
            pairs.chunked(2).flatMap { pair ->
                transform(pair[1], pair[0])?.let { listOf(pair[0], it) }.orEmpty()
            },
        )
}

private fun MutableMap<String, JsonElement>.mapValues(
    key: String,
    transform: (JsonElement) -> JsonElement?,
) = mapValues(key) { value, _ -> transform(value) }

internal fun GameState.finishDecoding(saved: JsonObject) {
    storage.remove("goldenEgg")?.let { retired ->
        val ordinary = storage["egg"] ?: 0
        if (retired !in 0..1_000_000_000 || ordinary !in 0..1_000_000_000 - retired) {
            throw InvalidSaveException("egg count")
        }
        if (retired > 0) storage["egg"] = ordinary + retired
    }
    storage.remove("junk-boot")
    storage.remove("junk-branch")
    if ("lastSprinklerID" !in saved) {
        selectedDecorationID?.let(Decoration::named)?.takeIf { it.isSprinkler }?.let { lastSprinklerID = it.id }
    }
    for (id in listOf(
        "modularRoof",
        "goldenNest",
        "oreVein",
        "automaticFeeder",
        "drinkingStation",
        "stableRail",
        "barnPanel",
        "hayScatter",
        "qualityScarecrow",
        "grainSacks",
        "flowerTufts",
    )) {
        if (selectedDecorationID == id) selectedDecorationID = null
        if (lastDecorationID == id) lastDecorationID = null
        selectedDecorationSizes.remove(id)
    }
    if (selectedDecorationID?.let(Decoration::named)?.offeredSizes?.isEmpty() == true) selectedDecorationID = null
    if (lastDecorationID?.let(Decoration::named)?.offeredSizes?.isEmpty() == true) lastDecorationID = null
    if (Lesson.pickHarvest in lessonsLearned) lessonsLearned.add(Lesson.grow)
    if (holdings.values.any { it.previousCloseCents == null }) lastStockPullAt = null
    if (mode == PlayMode.landscape && selectedLandscape == LandscapeTool.tree) selectedTerrain = LandscapeTool.tree
    if (saved["career"] == null || saved["career"] == JsonNull) {
        career =
            CareerRecord().also { record ->
                record.plantedCropIDs.addAll(plots.values.map { it.cropID })
                record.plantedCropIDs.addAll(Crop.all.filter { it.id in storage }.map { it.id })
                for (kind in FishKind.all) {
                    if (StorageItem.Fish(kind).id in storage) record.heaviestCatches[kind.id] = 0.0
                }
                record.builtBuildingIDs.addAll(buildings.values)
                record.placedDecorationIDs.addAll(decorations.values.map { it.decorationID })
                record.workedAnimalIDs.addAll(pets.values.map { it.breed })
                record.xpHistory = listOf(XPSample(playTime, xp))
            }
    }
    val savedCareer = saved["career"] as? JsonObject
    if (savedCareer?.get("placedTerrain") == null || savedCareer["placedTerrain"] == JsonNull) {
        career.placedTerrain.addAll(water.values.map(CareerRecord::terrainID))
        career.placedTerrain.addAll(groundCover.values.map { it.rawValue })
        if (farmland.isNotEmpty()) career.placedTerrain.add("farmland")
        for (tree in trees.values) {
            if (!tree.isNaturallyGenerated) {
                career.placedTerrain.add(CareerRecord.terrainID(tree.tree, tree.color))
            }
        }
    }
}

internal fun GameState.keepBottomRowPlots() {
    plots =
        plots
            .filter { (tile, plot) ->
                plot.origin?.let { tile.row == it.row + plot.side - 1 } ?: true
            }.toMutableMap()
}
