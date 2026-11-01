package com.hunter.stockfarmer.ui.fishing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudIcons
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.hudColor
import com.hunter.stockfarmer.ui.theme.hudPanel
import java.util.Locale

/** The pill that takes the place of the money and level while a fish is on the line: the bar, and how to fill it the first time. */
@Composable
internal fun FishingPill(
    mode: FishingMode,
    modifier: Modifier = Modifier,
) {
    val cast = mode.fishing.cast
    Column(
        modifier
            .hudPanel(16.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${cast.kind.rarity.displayName} catch meter"
                progressBarRangeInfo = ProgressBarRangeInfo(mode.fishing.progress.toFloat(), 0f..1f)
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            "Catch Meter",
            style = HudType.subheadline.copy(fontWeight = FontWeight.Bold, color = Color.White),
        )
        CatchBar(mode)
        BasicText(
            cast.kind.rarity.displayName,
            style = HudType.subheadline.copy(fontWeight = FontWeight.Bold, color = cast.kind.rarity.hudColor),
        )
        if (cast.showsHint) {
            BasicText(
                "Hold the rod over the fish",
                style = HudType.caption.copy(fontWeight = FontWeight.SemiBold, color = Theme.textSecondary),
            )
        }
    }
}

/** The bar starts the game's green and turns yellow, orange and red as it nears the end: round the hues from green down to red. */
internal fun catchBarColor(progress: Double): Color {
    val amount = progress.toFloat().coerceIn(0f, 1f)
    return Color.hsv(0.332f * (1 - amount) * 360, 0.48f + 0.4f * amount, 1f)
}

@Composable
private fun CatchBar(mode: FishingMode) {
    Canvas(Modifier.size(200.dp, 12.dp)) {
        // Read here so the bar redraws as it fills.
        mode.seconds
        val amount =
            mode.fishing.progress
                .toFloat()
                .coerceIn(0f, 1f)
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(Color.Black.copy(alpha = 0.45f), cornerRadius = radius)
        if (amount > 0) {
            drawRoundRect(
                catchBarColor(amount.toDouble()),
                size = Size(size.width * amount, size.height),
                cornerRadius = radius,
            )
        }
        val inset = 0.5.dp.toPx()
        drawRoundRect(
            Theme.panelBorder,
            topLeft = Offset(inset, inset),
            size = Size(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = radius,
            style = Stroke(1.dp.toPx()),
        )
    }
}

/**
 * A ring where the fishing rod tool works, lit while it is near enough to the fish for the bar to fill. The tool itself is the
 * one in the tool circle, dragged as ever, so nothing here takes touches.
 */
@Composable
internal fun FishingRing(
    mode: FishingMode,
    modifier: Modifier = Modifier,
) {
    if (!mode.isFishing || mode.isCaught) return
    val fishing = mode.fishing
    Canvas(modifier.fillMaxSize()) {
        mode.seconds
        val rod = fishing.rod ?: return@Canvas
        drawCircle(
            if (fishing.isOverFish) Theme.accent else Color.White.copy(alpha = 0.5f),
            fishing.reach.dp.toPx(),
            Offset(rod.x.dp.toPx(), rod.y.dp.toPx()),
            style = Stroke(3.dp.toPx()),
        )
    }
}

/** What is said over a fish just caught, and the two things to do with it. */
@Composable
internal fun FishingCatch(
    mode: FishingMode,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(mode.isFishing && mode.isCaught, modifier.fillMaxSize()) {
        val cast = mode.fishing.cast
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            // The message and the fish's weight are in one pill, high enough not to cover the fish.
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.4f), contentAlignment = Alignment.Center) {
                CatchDetails(mode)
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.48f),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CatchButton(
                        "Sell · ${cast.sellPrice.dollars}",
                        HudIcons.dollarsignCircle,
                    ) { mode.land(selling = true) }
                    CatchButton("Store", HudIcons.trayAndArrowDownFill) { mode.land(selling = false) }
                }
            }
        }
    }
}

internal fun catchMessage(kindName: String): String =
    "You caught ${if (kindName.first() in "AEIOU") "an" else "a"} $kindName!"

@Composable
private fun CatchDetails(mode: FishingMode) {
    val cast = mode.fishing.cast
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .hudPanel(18.dp)
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            catchMessage(cast.kind.name),
            style =
                HudType.title2.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
        )
        BasicText(
            cast.kind.rarity.displayName,
            style = HudType.caption.copy(fontWeight = FontWeight.Bold, color = cast.kind.rarity.hudColor),
        )
        BasicText(
            String.format(Locale.US, "%.1f lb", cast.weight),
            style = HudType.headline.copy(color = Color.White),
        )
        BasicText(
            buildAnnotatedString {
                append("Heavier than ")
                withStyle(SpanStyle(color = cast.kind.weightRarity(cast.weight).hudColor)) {
                    append(String.format(Locale.US, "%.1f%%", cast.kind.weightPercentile(cast.weight) * 100))
                }
                append(" of this species")
            },
            style = HudType.caption.copy(color = Color.White, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun CatchButton(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .hudPanel(16.dp)
            .clickable(
                interactionSource =
                    remember {
                        MutableInteractionSource()
                    },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ).padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudIcon(icon, Theme.textPrimary, size = 20.dp)
        BasicText(title, style = HudType.headline.copy(color = Theme.textPrimary))
    }
}
