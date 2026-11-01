package com.hunter.stockfarmer.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.LevelUnlocks
import com.hunter.stockfarmer.core.rules.isPlacing
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.core.rules.selectDecorating
import com.hunter.stockfarmer.core.rules.selectFertilizer
import com.hunter.stockfarmer.core.rules.selectTerrain
import com.hunter.stockfarmer.game.FarmSlots
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.cancelPlacement
import com.hunter.stockfarmer.game.confirmPlacement
import com.hunter.stockfarmer.ui.fishing.FishingOverlay
import com.hunter.stockfarmer.ui.menus.Menu
import com.hunter.stockfarmer.ui.menus.MenusHost
import com.hunter.stockfarmer.ui.screens.CareerScreen
import com.hunter.stockfarmer.ui.screens.DaySummaryBanner
import com.hunter.stockfarmer.ui.screens.LevelUpScreen
import com.hunter.stockfarmer.ui.screens.LocalScreenAudio
import com.hunter.stockfarmer.ui.screens.MarketQuestScreen
import com.hunter.stockfarmer.ui.screens.QuestBanner
import com.hunter.stockfarmer.ui.screens.QuestReward
import com.hunter.stockfarmer.ui.screens.SaveSlotsScreen
import com.hunter.stockfarmer.ui.screens.SettingsScreen
import com.hunter.stockfarmer.ui.screens.SoundCenterScreenAudio
import com.hunter.stockfarmer.ui.screens.StockMarketScreen
import com.hunter.stockfarmer.ui.sheets.SheetHost
import kotlinx.coroutines.delay
import kotlin.concurrent.thread

/** What the heads-up display shows, read from the game a few times a second. */
private data class HudState(
    val money: Double,
    val level: Int,
    val levelProgress: Float,
    val mode: PlayMode,
    val harvestTool: HarvestTool,
    val selectedCropID: String,
    val isPlacing: Boolean,
) {
    /** What the tool circle says: the thing in hand. */
    val toolLabel: String
        get() =
            when (mode) {
                PlayMode.plant -> "Seed"
                PlayMode.harvest -> harvestTool.displayName
                PlayMode.landscape -> "Terrain"
                PlayMode.build -> "Build"
                PlayMode.decorate -> "Decor"
                PlayMode.fertilize -> "Soil"
            }
}

private fun GameSession.hud(): HudState =
    act { engine ->
        val state = engine.state
        val (earned, span) = LevelRules.progress(state.xp)
        HudState(
            money = state.money,
            level = state.level,
            levelProgress = earned.toFloat() / span.coerceAtLeast(1),
            mode = state.mode,
            harvestTool = state.harvestTool,
            selectedCropID = state.selectedCropID,
            isPlacing = engine.isPlacing,
        )
    }

/** The full screens the HUD can open over the farm. */
private enum class HudScreen(
    val label: String,
) {
    Quests("Quests"),
    Stocks("Stocks"),
    Career("Career"),
    Farms("Farms"),
    Settings("Settings"),
}

private const val REFRESH_MILLIS = 250L
private val TOOL_CIRCLE_BOTTOM = 132.dp

@Composable
fun Hud(
    session: GameSession,
    sound: SoundCenter,
    farms: FarmSlots,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hud by remember { mutableStateOf(session.hud()) }
    var menu by remember { mutableStateOf<Menu?>(null) }
    var screen by remember { mutableStateOf<HudScreen?>(null) }
    var levelUnlocks by remember { mutableStateOf<LevelUnlocks?>(null) }
    var rewards by remember { mutableStateOf(emptyList<QuestReward>()) }
    var daySummary by remember { mutableStateOf<DaySummary?>(null) }
    LaunchedEffect(session) {
        while (true) {
            hud = session.hud()
            delay(REFRESH_MILLIS)
        }
    }
    val cast by session.casts.collectAsState()
    val sheet by session.sheets.collectAsState()
    LaunchedEffect(session) { session.newDays.collect { daySummary = it } }
    LaunchedEffect(session) {
        session.events.collect { events ->
            events.filterIsInstance<GameEvent.LeveledUp>().lastOrNull()?.let {
                levelUnlocks = LevelUnlocks.unlocked(it.from, it.level)
            }
            QuestReward.from(events).takeIf { it.isNotEmpty() }?.let { rewards = it }
        }
    }
    Box(modifier) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                StatusBar(hud)
                ScreenButtons(
                    onStorage = { menu = Menu.Storage },
                    onPhoto = { takeFarmPhoto(context, session) },
                    onScreen = { screen = it },
                )
            }
            Column {
                if (hud.isPlacing) PlacementBar(session)
                if (hud.mode == PlayMode.plant) SeedPicker(session, hud)
                if (hud.mode == PlayMode.harvest) HarvestToolPicker(session, hud)
                ToolBar(session, hud, onMenu = { menu = it })
            }
        }
        ToolCircle(
            session,
            hud.toolLabel,
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = TOOL_CIRCLE_BOTTOM),
        )
        menu?.let { open -> Overlay { MenusHost(session, open, onClose = { menu = null }) } }
        screen?.let { open ->
            val audio = remember(sound) { SoundCenterScreenAudio(sound) }
            Overlay {
                CompositionLocalProvider(LocalScreenAudio provides audio) {
                    val close = { screen = null }
                    when (open) {
                        HudScreen.Quests -> MarketQuestScreen(session, close)
                        HudScreen.Stocks -> StockMarketScreen(session, close)
                        HudScreen.Career -> CareerScreen(session, close)
                        HudScreen.Farms -> SaveSlotsScreen(session, farms, close)
                        HudScreen.Settings -> SettingsScreen(session, close)
                    }
                }
            }
        }
        levelUnlocks?.let { unlocks ->
            Overlay { LevelUpScreen(session, onClose = { levelUnlocks = null }, unlocks = unlocks) }
        }
        sheet?.let { open ->
            Overlay { SheetHost(session, open, onClose = { session.sheets.value = null }) }
        }
        cast?.let { hooked ->
            FishingOverlay(session, hooked, onFinish = { events ->
                session.publish(events)
                session.casts.value = null
            })
        }
        QuestBanner(session, onClose = { rewards = emptyList() }, rewards = rewards)
        DaySummaryBanner(session, onClose = { daySummary = null }, summary = daySummary)
    }
}

/** A full screen above the farm and the HUD. */
@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
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
private fun ScreenButtons(
    onStorage: () -> Unit,
    onPhoto: () -> Unit,
    onScreen: (HudScreen) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onStorage) { Text("Storage") }
        TextButton(onClick = onPhoto) { Text("Photo") }
        HudScreen.entries.forEach { TextButton(onClick = { onScreen(it) }) { Text(it.label) } }
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
private fun HarvestToolPicker(
    session: GameSession,
    hud: HudState,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        items(HarvestTool.entries, key = { it.name }) { tool ->
            FilterChip(
                selected = tool == hud.harvestTool,
                onClick = { session.act { it.select(tool) } },
                label = { Text(tool.displayName) },
            )
        }
    }
}

@Composable
private fun ToolBar(
    session: GameSession,
    hud: HudState,
    onMenu: (Menu) -> Unit,
) {
    val tools =
        listOf<Triple<String, Boolean, () -> Unit>>(
            Triple("Plant", hud.mode == PlayMode.plant) { session.act { it.setMode(PlayMode.plant) } },
            Triple("Harvest", hud.mode == PlayMode.harvest) { session.act { it.select(hud.harvestTool) } },
            Triple("Terrain", hud.mode == PlayMode.landscape) {
                session.act { it.selectTerrain() }
                onMenu(Menu.Terrain)
            },
            Triple("Build", hud.mode == PlayMode.build) {
                session.act { it.setMode(PlayMode.build) }
                onMenu(Menu.Build)
            },
            Triple("Decorate", hud.mode == PlayMode.decorate) {
                session.act { it.selectDecorating() }
                onMenu(Menu.Decorate)
            },
            Triple("Fertilize", hud.mode == PlayMode.fertilize) { session.act { it.selectFertilizer() } },
        )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tools, key = { it.first }) { (label, selected, onClick) ->
            FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
        }
    }
}

/** Shown while something is being placed: put it down where the green tiles are, or put it away. */
@Composable
private fun PlacementBar(session: GameSession) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { session.confirmPlacement() }) { Text("Place") }
        OutlinedButton(onClick = { session.cancelPlacement() }) { Text("Cancel") }
    }
}

private const val PHOTO_PIXELS = 4096
private const val SMALLER_PHOTO_PIXELS = 2048

/** Takes a picture of the whole farm and saves it to the gallery, trying a smaller one if the device is short of memory. */
private fun takeFarmPhoto(
    context: Context,
    session: GameSession,
) {
    val main = Handler(Looper.getMainLooper())

    fun tell(message: String) = main.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }

    fun save(png: ByteArray) =
        thread {
            tell(if (PhotoSaver.save(context, png)) "Farm photo saved" else "The farm photo could not be saved")
        }
    session.photos.request(PHOTO_PIXELS) { large ->
        if (large != null) {
            save(large)
        } else {
            session.photos.request(SMALLER_PHOTO_PIXELS) { small ->
                if (small != null) save(small) else tell("The farm photo could not be created. Please try again.")
            }
        }
    }
}
