package com.hunter.stockfarmer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.core.rules.selectDecorating
import com.hunter.stockfarmer.core.rules.selectFertilizer
import com.hunter.stockfarmer.core.rules.selectTerrain
import com.hunter.stockfarmer.game.GameSession
import kotlinx.coroutines.delay

/** What the heads-up display shows, read from the game a few times a second. */
private data class HudState(
    val money: Double,
    val level: Int,
    val levelProgress: Float,
    val mode: PlayMode,
    val harvestTool: HarvestTool,
    val selectedCropID: String,
)

private fun GameSession.hud(): HudState =
    read { state ->
        val (earned, span) = LevelRules.progress(state.xp)
        HudState(
            money = state.money,
            level = state.level,
            levelProgress = earned.toFloat() / span.coerceAtLeast(1),
            mode = state.mode,
            harvestTool = state.harvestTool,
            selectedCropID = state.selectedCropID,
        )
    }

private const val REFRESH_MILLIS = 250L

@Composable
fun Hud(
    session: GameSession,
    modifier: Modifier = Modifier,
) {
    var hud by remember { mutableStateOf(session.hud()) }
    LaunchedEffect(session) {
        while (true) {
            hud = session.hud()
            delay(REFRESH_MILLIS)
        }
    }
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        StatusBar(hud)
        Column {
            if (hud.mode == PlayMode.plant) SeedPicker(session, hud)
            ToolBar(session, hud)
        }
    }
}

@Composable
private fun StatusBar(hud: HudState) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(hud.money.dollars, style = MaterialTheme.typography.titleLarge)
            Text("Level ${hud.level}", style = MaterialTheme.typography.titleMedium)
        }
        LinearProgressIndicator(progress = { hud.levelProgress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SeedPicker(
    session: GameSession,
    hud: HudState,
) {
    val seeds = remember(hud.level) { Crop.all.filter { !it.isFruitTree && it.unlockLevel <= hud.level } }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        items(seeds, key = { it.id }) { crop ->
            FilterChip(
                selected = crop.id == hud.selectedCropID,
                onClick = { session.act { it.select(crop) } },
                label = { Text(crop.name) },
            )
        }
    }
}

@Composable
private fun ToolBar(
    session: GameSession,
    hud: HudState,
) {
    val tools =
        listOf<Triple<String, Boolean, () -> Unit>>(
            Triple("Plant", hud.mode == PlayMode.plant) { session.act { it.setMode(PlayMode.plant) } },
            Triple("Harvest", hud.mode == PlayMode.harvest) { session.act { it.select(HarvestTool.scythe) } },
            Triple("Terrain", hud.mode == PlayMode.landscape) { session.act { it.selectTerrain() } },
            Triple("Build", hud.mode == PlayMode.build) { session.act { it.setMode(PlayMode.build) } },
            Triple("Decorate", hud.mode == PlayMode.decorate) { session.act { it.selectDecorating() } },
            Triple("Fertilize", hud.mode == PlayMode.fertilize) { session.act { it.selectFertilizer() } },
        )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tools, key = { it.first }) { (label, selected, onClick) ->
            FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
        }
    }
}
