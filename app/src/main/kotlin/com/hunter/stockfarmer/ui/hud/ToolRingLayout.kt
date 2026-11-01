package com.hunter.stockfarmer.ui.hud

import kotlin.math.cos
import kotlin.math.sin

/** What the first ring offers: a tool to pick up, or one of the screens that are not tools. */
sealed interface Satellite {
    val title: String

    data class OfTool(
        val tool: HudTool,
    ) : Satellite {
        override val title get() = tool.title
    }

    data object Storage : Satellite {
        override val title = "Storage"
    }

    data object Settings : Satellite {
        override val title = "Settings"
    }

    data object Time : Satellite {
        override val title = "Time"
    }
}

/** One button fanned out around the tool circle: [angle] is degrees counterclockwise from straight right of its middle. */
data class RingSlot(
    val satellite: Satellite,
    val angle: Double,
    val radius: Float,
    val size: Float,
)

/** Where the ring's buttons go, in dp before the HUD scale (iOS `ToolRing`). The tools sit on the outer arc, the rest on an inner one. */
object ToolRingLayout {
    const val TOOL_RADIUS = 222f
    const val TOOL_SIZE = 68f
    const val OTHER_RADIUS = 125f
    const val OTHER_SIZE = 68f
    val toolAngles = listOf(90.0, 72.0, 54.0, 36.0, 18.0, 0.0)
    val otherAngles = listOf(80.0, 45.0, 10.0)

    val slots: List<RingSlot> =
        HudTool.entries.zip(toolAngles).map { (tool, angle) ->
            RingSlot(Satellite.OfTool(tool), angle, TOOL_RADIUS, TOOL_SIZE)
        } +
            listOf(Satellite.Storage, Satellite.Settings, Satellite.Time)
                .zip(otherAngles)
                .map { (satellite, angle) -> RingSlot(satellite, angle, OTHER_RADIUS, OTHER_SIZE) }

    /** The width and height of the square the ring is laid out in. */
    fun span(scale: Float): Float = (TOOL_RADIUS + TOOL_SIZE) * scale

    /** How far the middle of the circle is in from the square's left and bottom edges. */
    fun inset(scale: Float): Float = TOOL_SIZE * scale / 2

    /** Where a button's center is, measured from the square's top left. */
    fun spot(
        slot: RingSlot,
        scale: Float,
    ): Pair<Float, Float> {
        val radians = Math.toRadians(slot.angle)
        return (inset(scale) + slot.radius * scale * cos(radians).toFloat()) to
            (span(scale) - inset(scale) - slot.radius * scale * sin(radians).toFloat())
    }
}
