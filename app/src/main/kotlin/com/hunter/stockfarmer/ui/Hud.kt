package com.hunter.stockfarmer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.LevelUnlocks
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.game.FarmSlots
import com.hunter.stockfarmer.game.Feedback
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.ToastMessage
import com.hunter.stockfarmer.game.cancelPlacement
import com.hunter.stockfarmer.game.confirmPlacement
import com.hunter.stockfarmer.ui.fishing.FishingOverlay
import com.hunter.stockfarmer.ui.hud.BannerBox
import com.hunter.stockfarmer.ui.hud.HUD_REFRESH_MILLIS
import com.hunter.stockfarmer.ui.hud.HudScreen
import com.hunter.stockfarmer.ui.hud.HudState
import com.hunter.stockfarmer.ui.hud.LevelHud
import com.hunter.stockfarmer.ui.hud.MenuInteractionRegions
import com.hunter.stockfarmer.ui.hud.MoneyHud
import com.hunter.stockfarmer.ui.hud.RingScreen
import com.hunter.stockfarmer.ui.hud.SaveStatusView
import com.hunter.stockfarmer.ui.hud.ScreenButtons
import com.hunter.stockfarmer.ui.hud.TimeSheet
import com.hunter.stockfarmer.ui.hud.ToolBar
import com.hunter.stockfarmer.ui.hud.ToolPalette
import com.hunter.stockfarmer.ui.hud.hud
import com.hunter.stockfarmer.ui.hud.observesOutsideTouches
import com.hunter.stockfarmer.ui.hud.takeFarmPhoto
import com.hunter.stockfarmer.ui.menus.Menu
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.MenusHost
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.rewards.HUDTarget
import com.hunter.stockfarmer.ui.rewards.LocalRewardFlights
import com.hunter.stockfarmer.ui.rewards.rewardTarget
import com.hunter.stockfarmer.ui.screens.CareerScreen
import com.hunter.stockfarmer.ui.screens.ChangelogScreen
import com.hunter.stockfarmer.ui.screens.DaySummaryBanner
import com.hunter.stockfarmer.ui.screens.LevelUpScreen
import com.hunter.stockfarmer.ui.screens.LocalScreenAudio
import com.hunter.stockfarmer.ui.screens.MarketQuestScreen
import com.hunter.stockfarmer.ui.screens.PerformanceMonitor
import com.hunter.stockfarmer.ui.screens.PerformanceScreen
import com.hunter.stockfarmer.ui.screens.PrivacyScreen
import com.hunter.stockfarmer.ui.screens.QuestBanner
import com.hunter.stockfarmer.ui.screens.QuestReward
import com.hunter.stockfarmer.ui.screens.SaveSlotsScreen
import com.hunter.stockfarmer.ui.screens.SettingsScreen
import com.hunter.stockfarmer.ui.screens.SoundCenterScreenAudio
import com.hunter.stockfarmer.ui.screens.StockMarketScreen
import com.hunter.stockfarmer.ui.sheets.SheetHost
import com.hunter.stockfarmer.ui.theme.PlacementButtons
import com.hunter.stockfarmer.ui.theme.ScalesHudForTablets
import com.hunter.stockfarmer.ui.theme.ToastView
import com.hunter.stockfarmer.ui.timelapse.TimelapseScreen
import kotlinx.coroutines.delay

/**
 * The heads-up display over the farm, in the composition order of iOS `GameView`: the money and level panels on top, the
 * placement buttons and the tool bar at the bottom, then the messages, and the screens and sheets above everything.
 */
@Composable
fun Hud(
    session: GameSession,
    sound: SoundCenter,
    farms: FarmSlots,
    performance: PerformanceMonitor,
    modifier: Modifier = Modifier,
    /** Why saving is failing, shown over the farm with a Retry; null while saving works. */
    saveError: String? = null,
    onRetrySave: () -> Unit = {},
) {
    val context = LocalContext.current
    var hud by remember { mutableStateOf(session.hud()) }
    var menu by remember { mutableStateOf<Menu?>(null) }
    var screen by remember { mutableStateOf<HudScreen?>(null) }
    var isTimeShown by remember { mutableStateOf(false) }
    var levelUnlocks by remember { mutableStateOf<LevelUnlocks?>(null) }
    var rewards by remember { mutableStateOf(emptyList<QuestReward>()) }
    var daySummary by remember { mutableStateOf<DaySummary?>(null) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var isDraggingTool by remember { mutableStateOf(false) }
    var closeRequests by remember { mutableIntStateOf(0) }
    val regions = remember { MenuInteractionRegions() }
    LaunchedEffect(session) {
        while (true) {
            hud = session.hud()
            delay(HUD_REFRESH_MILLIS)
        }
    }
    val cast by session.casts.collectAsState()
    val sheet by session.sheets.collectAsState()
    val confirmation by session.confirmations.collectAsState()
    LaunchedEffect(session) { session.newDays.collect { daySummary = it } }
    LaunchedEffect(session) {
        session.events.collect { events ->
            Feedback.toast(events)?.let { toast = it }
            events.filterIsInstance<GameEvent.LeveledUp>().lastOrNull()?.let {
                levelUnlocks = LevelUnlocks.unlocked(it.from, it.level)
            }
            Feedback.questBanner(events)?.let { rewards = it }
        }
    }
    LaunchedEffect(toast) {
        val shown = toast ?: return@LaunchedEffect
        delay(shown.durationMillis)
        if (toast == shown) toast = null
    }
    val opensOverFarm = menu != null || screen != null || isTimeShown || sheet != null
    LaunchedEffect(opensOverFarm) { if (opensOverFarm) closeRequests++ }
    ScalesHudForTablets {
        Box(modifier) {
            StatusAndBars(
                session = session,
                hud = hud,
                regions = regions,
                isDraggingTool = isDraggingTool,
                closeRequests = closeRequests,
                onCloseMenus = { closeRequests++ },
                onCareer = { screen = HudScreen.Career },
                onStorage = { menu = Menu.Storage },
                onPhoto = { takeFarmPhoto(context, session) { toast = ToastMessage(it) } },
                onScreen = { screen = it },
                onRingScreen = {
                    when (it) {
                        RingScreen.Storage -> menu = Menu.Storage
                        RingScreen.Settings -> screen = HudScreen.Settings
                        RingScreen.Time -> isTimeShown = true
                    }
                },
                onDraggingChanged = { isDraggingTool = it },
            )
            menu?.let { open -> Overlay { MenusHost(session, open, onClose = { menu = null }) } }
            screen?.let { open ->
                val audio = remember(sound) { SoundCenterScreenAudio(sound) }
                Overlay {
                    CompositionLocalProvider(LocalScreenAudio provides audio) {
                        val close = { screen = null }
                        when (open) {
                            HudScreen.Quests -> {
                                MarketQuestScreen(session, close)
                            }

                            HudScreen.Stocks -> {
                                StockMarketScreen(session, close)
                            }

                            HudScreen.Career -> {
                                CareerScreen(session, close)
                            }

                            HudScreen.Farms -> {
                                SaveSlotsScreen(session, farms, close)
                            }

                            HudScreen.Timelapse -> {
                                TimelapseScreen(session, close)
                            }

                            HudScreen.Performance -> {
                                PerformanceScreen(
                                    session,
                                    performance,
                                    close,
                                    recordingContext = { "Stock Farmer" },
                                )
                            }

                            HudScreen.Changelog -> {
                                ChangelogScreen(session, close)
                            }

                            HudScreen.Privacy -> {
                                PrivacyScreen(session, close)
                            }

                            HudScreen.Settings -> {
                                SettingsScreen(session, close)
                            }
                        }
                    }
                }
            }
            if (isTimeShown) TimeSheet(session, onDismiss = { isTimeShown = false })
            levelUnlocks?.let { unlocks ->
                Overlay { LevelUpScreen(session, onClose = { levelUnlocks = null }, unlocks = unlocks) }
            }
            confirmation?.let { asked ->
                AlertDialog(
                    onDismissRequest = { session.confirmations.value = null },
                    title = { Text(asked.title) },
                    text = { Text(asked.message) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                session.confirmations.value = null
                                asked.action()
                            },
                        ) { Text(asked.confirmTitle) }
                    },
                    dismissButton = { TextButton(onClick = { session.confirmations.value = null }) { Text("Cancel") } },
                )
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
            ToastBanner(session, toast) { toast = null }
            QuestBanner(session, onClose = { rewards = emptyList() }, rewards = rewards)
            DaySummaryBanner(session, onClose = { daySummary = null }, summary = daySummary)
            if (saveError != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SaveStatusView(saveError, onRetrySave, Modifier.padding(16.dp))
                }
            }
        }
    }
}

/** The panels and controls on the farm itself: status on top, placement buttons and the tool bar below. */
@Composable
private fun StatusAndBars(
    session: GameSession,
    hud: HudState,
    regions: MenuInteractionRegions,
    isDraggingTool: Boolean,
    closeRequests: Int,
    onCloseMenus: () -> Unit,
    onCareer: () -> Unit,
    onStorage: () -> Unit,
    onPhoto: () -> Unit,
    onScreen: (HudScreen) -> Unit,
    onRingScreen: (RingScreen) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
) {
    val flights = LocalRewardFlights.current
    val money = flights?.displayedMoney(hud.money) ?: hud.money
    val xp = flights?.displayedXP(hud.xp) ?: hud.xp
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                MoneyHud(
                    money,
                    pulse = 0,
                    modifier =
                        Modifier
                            .rewardTarget(
                                HUDTarget.Money,
                            ).observesOutsideTouches(true, regions, onCloseMenus),
                )
                LevelHud(
                    xp,
                    pulse = 0,
                    onClick = { if (!hud.isPlacing) onCareer() },
                    modifier =
                        Modifier
                            .rewardTarget(
                                HUDTarget.Level,
                            ).observesOutsideTouches(true, regions, onCloseMenus),
                )
            }
            if (!hud.isPlacing) {
                ScreenButtons(
                    onStorage,
                    onPhoto,
                    onScreen,
                    Modifier.observesOutsideTouches(true, regions, onCloseMenus),
                )
            }
        }
        if (hud.isPlacing && !isDraggingTool) {
            PlacementButtons(
                onConfirm = { session.confirmPlacement() },
                onCancel = { session.cancelPlacement() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).padding(bottom = 8.dp),
                cost = hud.placementCost,
            )
        }
        if (!hud.isPlacing || isDraggingTool) {
            ToolBar(
                session = session,
                hud = hud,
                regions = regions,
                onRingScreen = onRingScreen,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                closeRequests = closeRequests,
                onDraggingChanged = onDraggingChanged,
            )
        }
    }
}

/** The message over the farm, with the implement it offers to pick up. */
@Composable
private fun ToastBanner(
    session: GameSession,
    toast: ToastMessage?,
    onDismiss: () -> Unit,
) {
    val loader = rememberMenuAssetLoader()
    val tool = toast?.tool
    BannerBox {
        ToastView(
            message = toast?.text,
            toolName = tool?.displayName,
            toolIcon =
                tool?.let { held ->
                    { MenuImage(ToolPalette.harvestPicture(held), loader, Modifier.size(36.dp)) }
                },
            onToolTap = {
                tool?.let { held -> session.act { it.select(held) } }
                onDismiss()
            },
        )
    }
}

/** A full screen above the farm and the HUD. */
@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
}
