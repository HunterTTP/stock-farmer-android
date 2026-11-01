package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.ShopItem

/** Every screen a menu can show; each opens on top of the last and Back returns to it (iOS `ToolRoute`). */
sealed interface ToolRoute {
    data class Item(
        val item: ShopItem,
    ) : ToolRoute

    data object Eraser : ToolRoute

    data class HarvestToolDetail(
        val tool: HarvestTool,
    ) : ToolRoute

    data class ToolUpgrade(
        val tool: HarvestTool,
    ) : ToolRoute

    data object Storage : ToolRoute

    data object Career : ToolRoute

    data object Settings : ToolRoute

    data object SoundSettings : ToolRoute

    data object GraphicsSettings : ToolRoute

    data object GameplaySettings : ToolRoute

    data object About : ToolRoute

    data object Performance : ToolRoute

    data object Timelapse : ToolRoute

    data object LoadGame : ToolRoute

    data object Privacy : ToolRoute

    data object Changelog : ToolRoute
}

/** The routes of one open tools sheet, the first being the screen it opened on. Android Back pops one; at the root it closes. */
data class ToolRouteStack(
    val routes: List<ToolRoute>,
) {
    constructor(root: ToolRoute) : this(listOf(root))

    init {
        require(routes.isNotEmpty()) { "A sheet always shows a screen" }
    }

    val root: ToolRoute get() = routes.first()
    val current: ToolRoute get() = routes.last()
    val canGoBack: Boolean get() = routes.size > 1

    /** Opens [route] on top of the current screen; a second push of the one already showing (a double tap) is ignored. */
    fun push(route: ToolRoute): ToolRouteStack = if (route == current) this else ToolRouteStack(routes + route)

    /** The stack one screen up, or null when the sheet is on its root and should close. */
    fun back(): ToolRouteStack? = if (canGoBack) ToolRouteStack(routes.dropLast(1)) else null
}
