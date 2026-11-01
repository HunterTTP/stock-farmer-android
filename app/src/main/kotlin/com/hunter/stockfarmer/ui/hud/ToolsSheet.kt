package com.hunter.stockfarmer.ui.hud

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.hunter.stockfarmer.game.FarmSlots
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.menus.ItemScreen
import com.hunter.stockfarmer.ui.menus.Menu
import com.hunter.stockfarmer.ui.menus.MenusHost
import com.hunter.stockfarmer.ui.menus.ToolUpgradeResult
import com.hunter.stockfarmer.ui.screens.AboutScreen
import com.hunter.stockfarmer.ui.screens.CareerScreen
import com.hunter.stockfarmer.ui.screens.ChangelogScreen
import com.hunter.stockfarmer.ui.screens.PerformanceMonitor
import com.hunter.stockfarmer.ui.screens.PerformanceScreen
import com.hunter.stockfarmer.ui.screens.PrivacyScreen
import com.hunter.stockfarmer.ui.screens.SaveSlotsScreen
import com.hunter.stockfarmer.ui.screens.SettingsScreen
import com.hunter.stockfarmer.ui.screens.SoundSettingsScreen
import com.hunter.stockfarmer.ui.theme.HudSheet
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.timelapse.TimelapseScreen

/**
 * One sheet opened on a single screen, such as an item's page, Storage or Settings, with deeper screens pushed on top of it
 * (iOS `ToolsSheet`). Android Back goes one screen up; on the first screen it closes the sheet. Each screen's own Close does the
 * same, so [onClose] is reached only from the first screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolsSheet(
    session: GameSession,
    farms: FarmSlots,
    performance: PerformanceMonitor,
    root: ToolRoute,
    onTakePhoto: ((String) -> Unit) -> Unit,
    onClose: () -> Unit,
    onSelected: () -> Unit = onClose,
    onUpgradeResult: (ToolUpgradeResult) -> Unit = {},
) {
    var stack by remember(root) { mutableStateOf(ToolRouteStack(root)) }
    val back = { stack.back()?.let { stack = it } ?: onClose() }
    val open = { route: ToolRoute -> stack = stack.push(route) }
    var photoMessage by remember { mutableStateOf<String?>(null) }
    val route = stack.current
    if (route == ToolRoute.Storage) {
        BackHandler(enabled = stack.canGoBack, onBack = back)
        MenusHost(session, Menu.Storage, onClose = back)
        return
    }
    val itemScreen =
        when (route) {
            is ToolRoute.Item -> ItemScreen.Item(route.item)
            ToolRoute.Eraser -> ItemScreen.Eraser
            is ToolRoute.HarvestToolDetail -> ItemScreen.Harvest(route.tool)
            is ToolRoute.ToolUpgrade -> ItemScreen.Upgrade(route.tool)
            else -> null
        }
    if (itemScreen != null) {
        BackHandler(enabled = stack.canGoBack, onBack = back)
        MenusHost(
            session = session,
            screen = itemScreen,
            onClose = back,
            onSelected = onSelected,
            onUpgradeResult = onUpgradeResult,
        )
        return
    }
    HudSheet(onDismiss = onClose) {
        BackHandler(enabled = stack.canGoBack, onBack = back)
        RouteScreen(route, session, farms, performance, open, { onTakePhoto { photoMessage = it } }, back)
    }
    photoMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { photoMessage = null },
            containerColor = Theme.launchBackground,
            title = {
                BasicText(
                    "Farm Photo",
                    style = HudType.headline.copy(fontWeight = FontWeight.Bold, color = Theme.textPrimary),
                )
            },
            text = {
                BasicText(
                    message,
                    style = HudType.body.copy(color = Theme.textSecondary),
                )
            },
            confirmButton = {
                PillButton(
                    title = "OK",
                    role = PillRole.neutral,
                    onClick = { photoMessage = null },
                )
            },
        )
    }
}

@Composable
private fun RouteScreen(
    route: ToolRoute,
    session: GameSession,
    farms: FarmSlots,
    performance: PerformanceMonitor,
    open: (ToolRoute) -> Unit,
    onPhoto: () -> Unit,
    back: () -> Unit,
) {
    when (route) {
        ToolRoute.Career -> CareerScreen(session, back)

        ToolRoute.Settings -> SettingsScreen(session, open, onPhoto, performance.snapshot.isRecording, back)

        ToolRoute.SoundSettings -> SoundSettingsScreen(session, back)

        ToolRoute.About -> AboutScreen(session, open, back)

        ToolRoute.Performance -> PerformanceScreen(session, performance, back, recordingContext = { "Stock Farmer" })

        ToolRoute.Timelapse -> TimelapseScreen(session, back)

        ToolRoute.LoadGame -> SaveSlotsScreen(session, farms, back)

        ToolRoute.Privacy -> PrivacyScreen(session, back)

        ToolRoute.Changelog -> ChangelogScreen(session, back)

        is ToolRoute.Item,
        ToolRoute.Eraser,
        is ToolRoute.HarvestToolDetail,
        is ToolRoute.ToolUpgrade,
        ToolRoute.Storage,
        -> Unit
    }
}
