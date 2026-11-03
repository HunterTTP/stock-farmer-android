package com.hunter.stockfarmer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.diagnostics.PerformanceMonitor
import com.hunter.stockfarmer.game.FarmSlots
import com.hunter.stockfarmer.game.Feedback
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.SheetTarget
import com.hunter.stockfarmer.game.ToastMessage
import com.hunter.stockfarmer.game.askToDestroyWhilePlacing
import com.hunter.stockfarmer.game.cancelPlacement
import com.hunter.stockfarmer.game.confirmPlacement
import com.hunter.stockfarmer.game.interactWithAnimal
import com.hunter.stockfarmer.settings.PlaySettings
import com.hunter.stockfarmer.ui.fishing.FishingCanvas
import com.hunter.stockfarmer.ui.fishing.FishingCatch
import com.hunter.stockfarmer.ui.fishing.FishingMode
import com.hunter.stockfarmer.ui.fishing.FishingPill
import com.hunter.stockfarmer.ui.fishing.FishingRing
import com.hunter.stockfarmer.ui.fishing.rememberFishingMode
import com.hunter.stockfarmer.ui.hud.BannerBox
import com.hunter.stockfarmer.ui.hud.HUD_REFRESH_MILLIS
import com.hunter.stockfarmer.ui.hud.HudState
import com.hunter.stockfarmer.ui.hud.HudTopSpacing
import com.hunter.stockfarmer.ui.hud.LevelHud
import com.hunter.stockfarmer.ui.hud.MenuInteractionRegions
import com.hunter.stockfarmer.ui.hud.MoneyHud
import com.hunter.stockfarmer.ui.hud.PanelCues
import com.hunter.stockfarmer.ui.hud.RingScreen
import com.hunter.stockfarmer.ui.hud.SaveStatusView
import com.hunter.stockfarmer.ui.hud.TimeSheet
import com.hunter.stockfarmer.ui.hud.ToolBar
import com.hunter.stockfarmer.ui.hud.ToolPalette
import com.hunter.stockfarmer.ui.hud.ToolRoute
import com.hunter.stockfarmer.ui.hud.ToolsSheet
import com.hunter.stockfarmer.ui.hud.hud
import com.hunter.stockfarmer.ui.hud.observesOutsideTouches
import com.hunter.stockfarmer.ui.hud.takeFarmPhoto
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.rewards.HUDTarget
import com.hunter.stockfarmer.ui.rewards.LocalRewardFlights
import com.hunter.stockfarmer.ui.rewards.rewardTarget
import com.hunter.stockfarmer.ui.screens.DaySummaryBanner
import com.hunter.stockfarmer.ui.screens.LevelUpScreen
import com.hunter.stockfarmer.ui.screens.LocalScreenAudio
import com.hunter.stockfarmer.ui.screens.QuestBanner
import com.hunter.stockfarmer.ui.screens.SoundCenterScreenAudio
import com.hunter.stockfarmer.ui.sheets.SheetHost
import com.hunter.stockfarmer.ui.theme.LocalHudScale
import com.hunter.stockfarmer.ui.theme.PlacementButtons
import com.hunter.stockfarmer.ui.theme.ScalesHudForTablets
import com.hunter.stockfarmer.ui.theme.ToastView

/**
 * The heads-up display over the farm, in composition order: the money and level panels on top, the
 * placement buttons and the tool bar at the bottom, then the messages, and the screens and sheets above everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val play = remember(context) { PlaySettings.of(context) }
    val hud by rememberPolledState(session, HUD_REFRESH_MILLIS) { session.hud(play.toolReach) }
    var isDraggingTool by remember { mutableStateOf(false) }
    val regions = remember { MenuInteractionRegions() }
    val cast by session.casts.collectAsStateWithLifecycle()
    val sheet by session.sheets.collectAsStateWithLifecycle()
    val confirmation by session.confirmations.collectAsStateWithLifecycle()
    val messages = rememberHudMessages(session)
    val navigation = rememberHudNavigation(isSheetOpen = sheet != null)
    val panels = remember(sound) { PanelCues { sound.play(it) } }
    val isPanelOpen = navigation.isOpen || sheet is SheetTarget.Building
    LaunchedEffect(isPanelOpen) { panels.update(isPanelOpen) }
    ScalesHudForTablets {
        BoxWithConstraints(modifier) {
            val fishing =
                cast?.let { hooked ->
                    rememberFishingMode(
                        session,
                        hooked,
                        maxWidth.value.toDouble(),
                        maxHeight.value.toDouble(),
                        onCaught = { sound.play(SoundCue.questComplete) },
                        onFinish = { events ->
                            session.publish(events)
                            if (events.isEmpty()) {
                                session.showToast(hooked.swamAwayMessage, durationMillis = Feedback.TOAST_MILLIS)
                            }
                            session.casts.value = null
                        },
                    )
                }
            // The water lies over the farm and under the menus, which stay on it.
            if (fishing != null) FishingCanvas(session, fishing, Modifier.fillMaxSize())
            StatusAndBars(
                session = session,
                fishing = fishing,
                hud = hud,
                play = play,
                regions = regions,
                isDraggingTool = isDraggingTool,
                closeRequests = navigation.closeRequests,
                onCloseMenus = navigation::closeMenus,
                onCareer = navigation::openCareer,
                onRingScreen = navigation::open,
                onOpenRoute = navigation::openFromList,
                onDraggingChanged = { isDraggingTool = it },
            )
            navigation.toolRoute?.let { root ->
                val audio = remember(sound) { SoundCenterScreenAudio(sound) }
                CompositionLocalProvider(LocalScreenAudio provides audio) {
                    ToolsSheet(
                        session = session,
                        farms = farms,
                        performance = performance,
                        root = root,
                        onTakePhoto = { report -> takeFarmPhoto(context, session, report) },
                        onClose = navigation::closeRoute,
                        onSelected = {
                            panels.closeQuietly()
                            navigation.selectedFromRoute()
                        },
                        onToast = { message ->
                            messages.toast = ToastMessage(message, durationMillis = 4_000L)
                        },
                        onUpgradeResult = { result ->
                            if (result.bought) {
                                sound.play(SoundCue.built)
                                messages.toast = ToastMessage(result.message)
                            }
                        },
                    )
                }
            }
            if (navigation.isTimeShown) TimeSheet(session, onDismiss = navigation::closeTime)
            messages.levelUnlocks?.takeIf { messages.rewards.isEmpty() }?.let { unlocks ->
                LevelUpScreen(session, onClose = { messages.levelUnlocks = null }, unlocks = unlocks)
            }
            confirmation?.let { ConfirmationDialog(session, it) }
            sheet?.let { open ->
                SheetHost(
                    session = session,
                    target = open,
                    onClose = { session.sheets.value = null },
                    onCloseQuietly = {
                        panels.closeQuietly()
                        session.sheets.value = null
                    },
                    onAnimalInteraction = session::interactWithAnimal,
                )
            }
            ToastBanner(session, messages.toast) { messages.toast = null }
            QuestBanner(session, onClose = { messages.rewards = emptyList() }, rewards = messages.rewards)
            DaySummaryBanner(session, onClose = { messages.daySummary = null }, summary = messages.daySummary)
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
    fishing: FishingMode?,
    hud: HudState,
    play: PlaySettings,
    regions: MenuInteractionRegions,
    isDraggingTool: Boolean,
    closeRequests: Int,
    onCloseMenus: () -> Unit,
    onCareer: () -> Unit,
    onRingScreen: (RingScreen) -> Unit,
    onOpenRoute: (ToolRoute) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
) {
    val flights = LocalRewardFlights.current
    val money = flights?.displayedMoney(hud.money) ?: hud.money
    val xp = flights?.displayedXP(hud.xp) ?: hud.xp
    val density = LocalDensity.current
    val insetTopDp =
        with(density) {
            WindowInsets.safeDrawing
                .getTop(this)
                .toDp()
                .value
        }
    val topGap = HudTopSpacing.extraTopPaddingDp(insetTopDp, LocalHudScale.current).dp
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = topGap, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // A fish on the line puts the catch meter in the place of the money and level.
                Crossfade(fishing?.isFishing == true, animationSpec = tween(FISHING_HUD_FADE_MILLIS)) { isFishing ->
                    if (isFishing && fishing != null) {
                        Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.Top) {
                            AnimatedVisibility(
                                !fishing.isCaught,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) { FishingPill(fishing) }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                            if (play.isMoneyShown) {
                                MoneyHud(
                                    money,
                                    pulse = 0,
                                    modifier =
                                        Modifier
                                            .rewardTarget(
                                                HUDTarget.Money,
                                            ).observesOutsideTouches(true, regions, onCloseMenus),
                                )
                            } else {
                                Spacer(Modifier)
                            }
                            if (play.isLevelShown) {
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
                        }
                    }
                }
            }
        }
        if (fishing != null) {
            FishingRing(fishing)
            FishingCatch(fishing)
        }
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (hud.isPlacing && !isDraggingTool) {
                PlacementButtons(
                    onConfirm = { session.confirmPlacement() },
                    onCancel = { session.cancelPlacement() },
                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomCenter,
                            ).padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                    cost = hud.placementCost,
                    onDestroy = if (hud.canDestroy) ({ session.askToDestroyWhilePlacing() }) else null,
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
                    onOpenRoute = onOpenRoute,
                    onDraggingChanged = onDraggingChanged,
                    fishing = fishing,
                )
            }
        }
    }
}

private const val FISHING_HUD_FADE_MILLIS = 350

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
