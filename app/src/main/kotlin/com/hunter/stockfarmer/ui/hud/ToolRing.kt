package com.hunter.stockfarmer.ui.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.ui.menus.MenuAssetLoader
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.screens.lessonPulse
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudIcons
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.LocalHudScale
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.darkGlass
import com.hunter.stockfarmer.ui.theme.newDot
import com.hunter.stockfarmer.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import java.time.Instant

/** What the ring needs from the game to draw its faces. */
internal data class RingState(
    val currentTool: HudTool,
    /** The tools that have something new. */
    val newTools: Set<HudTool> = emptySet(),
    /** The tool the first-time lesson points at. */
    val lessonTool: HudTool? = null,
    val isStorageLesson: Boolean = false,
    val storageDescription: String = "",
    /** The farm clock's minute of the day, redrawn once a second while the ring is open. */
    val minuteOfDay: (Instant) -> Double,
)

private const val STAGGER_MILLIS = 20L
private const val SPRING_DAMPING = 0.75f
private const val SPRING_STIFFNESS = 321.5f
private const val CLOSED_SCALE = 0.3f

/**
 * The ring of buttons that fans out around the tool circle when it is tapped. The tools sit on the outer arc and
 * Storage, Settings and the clock on an inner one, both facing up and to the right, away from the corner the circle sits in.
 */
@Composable
internal fun ToolRing(
    isOpen: Boolean,
    state: RingState,
    loader: MenuAssetLoader,
    regions: MenuInteractionRegions,
    onPick: (Satellite) -> Unit,
    modifier: Modifier = Modifier,
    isFishing: Boolean = false,
) {
    val scale = LocalHudScale.current
    val span = ToolRingLayout.span(scale).dp
    Box(
        modifier
            .padding(
                start = ((ToolCircleMetrics.DIAMETER - ToolRingLayout.TOOL_SIZE) * scale / 2).dp,
                bottom = ((ToolCircleMetrics.DIAMETER - ToolRingLayout.TOOL_SIZE) * scale / 2).dp,
            ).size(span),
    ) {
        (if (isFishing) ToolRingLayout.fishingSlots else ToolRingLayout.slots).forEachIndexed { index, slot ->
            key(slot.satellite.title) {
                RingButton(index, slot, isOpen, state, loader, regions, scale) { onPick(slot.satellite) }
            }
        }
    }
}

@Composable
private fun RingButton(
    index: Int,
    slot: RingSlot,
    isOpen: Boolean,
    state: RingState,
    loader: MenuAssetLoader,
    regions: MenuInteractionRegions,
    scale: Float,
    onClick: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(isOpen, reduceMotion) {
        if (reduceMotion) {
            progress.animateTo(if (isOpen) 1f else 0f, tween(150))
        } else {
            delay(STAGGER_MILLIS * index)
            progress.animateTo(if (isOpen) 1f else 0f, spring(SPRING_DAMPING, SPRING_STIFFNESS))
        }
    }
    val size = slot.size * scale
    val (spotX, spotY) = ToolRingLayout.spot(slot, scale)
    val inset = ToolRingLayout.inset(scale)
    val span = ToolRingLayout.span(scale)
    val density = LocalDensity.current.density
    val isShown = isOpen || progress.value > 0f
    if (!isShown) {
        regions.set("ring-${slot.satellite.title}", null)
        return
    }
    Box(
        Modifier
            .offset((spotX - size / 2).dp, (spotY - size / 2).dp)
            .size(size.dp)
            .menuInteractionRegion(regions, "ring-${slot.satellite.title}", isEnabled = isOpen)
            .then(
                if (isOpen) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                val p = if (reduceMotion) 1f else progress.value
                val grown = if (reduceMotion) 1f else CLOSED_SCALE + (1 - CLOSED_SCALE) * p
                scaleX = grown
                scaleY = grown
                translationX = (inset - spotX) * (1 - p) * density
                translationY = ((span - inset) - spotY) * (1 - p) * density
                alpha = progress.value.coerceIn(0f, 1f)
            },
        ) { Face(slot, size.dp, state, loader, scale) }
    }
}

@Composable
private fun Face(
    slot: RingSlot,
    size: Dp,
    state: RingState,
    loader: MenuAssetLoader,
    scale: Float,
) {
    when (val satellite = slot.satellite) {
        is Satellite.OfTool -> {
            val tool = satellite.tool
            RingFace(
                title = tool.title,
                size = size,
                isSelected = state.currentTool == tool,
                isNew = if (state.lessonTool != null) state.lessonTool == tool else tool in state.newTools,
                pulses = state.lessonTool == tool,
                scale = scale,
            ) { MenuImage(ToolPalette.menuPicture(tool), loader, Modifier.size((36 * scale).dp)) }
        }

        Satellite.Storage -> {
            RingFace(
                "Storage",
                size,
                isNew = state.isStorageLesson,
                pulses = state.isStorageLesson,
                scale = scale,
                detail = state.storageDescription,
            ) {
                HudIcon(HudIcons.bagFill, Color.White, size = (30 * scale).dp)
            }
        }

        Satellite.Settings -> {
            RingFace("Settings", size, scale = scale) { Symbol("⚙", scale, Color.White) }
        }

        Satellite.ExitFishing -> {
            RingFace("Exit", size, scale = scale) {
                HudIcon(HudIcons.xmark, Color.White, size = (30 * scale).dp)
            }
        }

        Satellite.Time -> {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(1_000)
                }
            }
            val minute = state.minuteOfDay(Instant.ofEpochMilli(now))
            val isNight = DayNightCycle.darkness(minute) > 0
            RingFace(DayNightCycle.clockText(minute), size, scale = scale, detail = "Time of day") {
                Symbol(if (isNight) "☾" else "☀", scale, if (isNight) Theme.blue else Theme.gold)
            }
        }
    }
}

@Composable
private fun Symbol(
    text: String,
    scale: Float,
    color: Color,
) {
    BasicText(text, style = HudType.title.copy(fontSize = (30 * scale).sp, color = color, textAlign = TextAlign.Center))
}

/** The face of one ring button: a glass circle with a small picture over its name. */
@Composable
private fun RingFace(
    title: String,
    size: Dp,
    scale: Float,
    isSelected: Boolean = false,
    isNew: Boolean = false,
    pulses: Boolean = false,
    detail: String = "",
    icon: @Composable () -> Unit,
) {
    Column(
        Modifier
            .lessonPulse(pulses)
            .size(size)
            .shadow(
                4.dp,
                CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.25f),
            ).darkGlass(CircleShape)
            .then(if (isSelected) Modifier.border(2.dp, Theme.accent, CircleShape) else Modifier)
            .newDot(isNew, Alignment.TopEnd, DpOffset((-2).dp, 2.dp))
            .padding(horizontal = 2.dp)
            .semantics { contentDescription = if (detail.isEmpty()) title else "$title, $detail" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size((36 * scale).dp), contentAlignment = Alignment.Center) { icon() }
        BasicText(
            title,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            autoSize = TextAutoSize.StepBased(minFontSize = (12 * scale * 0.6f).sp, maxFontSize = (12 * scale).sp),
            style =
                HudType.caption.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.textPrimary,
                    textAlign = TextAlign.Center,
                ),
        )
    }
}
