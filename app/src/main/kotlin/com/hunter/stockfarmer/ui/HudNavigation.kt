package com.hunter.stockfarmer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hunter.stockfarmer.ui.hud.RingScreen
import com.hunter.stockfarmer.ui.hud.ToolRoute

/**
 * Which screen the tool bar has opened over the farm, and the close requests that fold its open menus away. A screen
 * opened from a list's detail keeps those menus open.
 */
@Stable
class HudNavigation {
    var toolRoute by mutableStateOf<ToolRoute?>(null)
        private set
    var isTimeShown by mutableStateOf(false)
        private set
    var closeRequests by mutableIntStateOf(0)
        private set
    private var isShowingDetailFromList = false

    val isOpen: Boolean get() = toolRoute != null || isTimeShown

    fun closeMenus() {
        closeRequests++
    }

    fun openCareer() {
        isShowingDetailFromList = false
        toolRoute = ToolRoute.Career
    }

    fun open(screen: RingScreen) {
        isShowingDetailFromList = false
        when (screen) {
            RingScreen.Storage -> toolRoute = ToolRoute.Storage
            RingScreen.Settings -> toolRoute = ToolRoute.Settings
            RingScreen.Time -> isTimeShown = true
        }
    }

    fun openFromList(route: ToolRoute) {
        isShowingDetailFromList = true
        toolRoute = route
    }

    fun closeRoute() {
        toolRoute = null
    }

    fun closeTime() {
        isTimeShown = false
    }

    /** A tool was picked from the screen: it closes, and so do the menus behind it. */
    fun selectedFromRoute() {
        toolRoute = null
        closeRequests++
    }

    internal fun opensOverFarmChanged(opens: Boolean) {
        if (!opens) {
            isShowingDetailFromList = false
        } else if (!isShowingDetailFromList) {
            closeRequests++
        }
    }
}

/** The navigation state; [isSheetOpen] says whether a building or animal sheet is up too. */
@Composable
fun rememberHudNavigation(isSheetOpen: Boolean): HudNavigation {
    val navigation = remember { HudNavigation() }
    val opensOverFarm = navigation.isOpen || isSheetOpen
    LaunchedEffect(opensOverFarm) { navigation.opensOverFarmChanged(opensOverFarm) }
    return navigation
}
