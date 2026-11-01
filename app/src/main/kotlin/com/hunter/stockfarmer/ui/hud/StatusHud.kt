package com.hunter.stockfarmer.ui.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.hudPanel
import java.text.NumberFormat
import java.util.Locale

/** The text on the money and level panels. */
object StatusText {
    /** US dollars, always two decimals. */
    fun currency(money: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(money)

    fun count(value: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

    /** "120 / 300 XP", or "Max level" at the last level. */
    fun levelCaption(
        level: Int,
        earned: Int,
        needed: Int,
    ): String = if (level >= LevelRules.maxLevel) "Max level" else "${count(earned)} / ${count(needed)} XP"
}

private const val THROB_SWELL = 0.2f

// A bouncy spring settling over 0.3s: damping 0.7, stiffness (2π / 0.3)².
private const val THROB_DAMPING = 0.7f
private const val THROB_STIFFNESS = 439f

/** Swells briefly each time [trigger] changes. */
@Composable
fun Modifier.throb(trigger: Int): Modifier {
    val swell = rememberThrob(trigger)
    return graphicsLayer {
        val scale = 1f + THROB_SWELL * swell.value
        scaleX = scale
        scaleY = scale
    }
}

@Composable
private fun rememberThrob(trigger: Int): Animatable<Float, *> {
    val swell = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        swell.animateTo(1f, tween(100))
        swell.animateTo(0f, spring(THROB_DAMPING, THROB_STIFFNESS))
    }
    return swell
}

/** Money in green. */
@Composable
fun MoneyHud(
    money: Double,
    pulse: Int,
    modifier: Modifier = Modifier,
) {
    val text = StatusText.currency(money)
    BasicText(
        text,
        modifier
            .throb(pulse)
            .height(52.dp)
            .hudPanel(16.dp)
            .padding(horizontal = 14.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .semantics { contentDescription = "Money $text" },
        maxLines = 1,
        overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 17.sp),
        style =
            HudType.headline.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Theme.money,
                fontFeatureSettings = "tnum",
            ),
    )
}

/** Level and XP progress in purple; tapping it opens the career. */
@Composable
fun LevelHud(
    xp: Int,
    pulse: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val level = LevelRules.levelForXP(xp)
    val (earned, needed) = LevelRules.progress(xp)
    val caption = StatusText.levelCaption(level, earned, needed)
    Column(
        modifier
            .throb(pulse)
            .width(118.dp)
            .height(52.dp)
            .hudPanel(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ).padding(horizontal = 12.dp)
            .semantics {
                contentDescription = "Level $level"
                stateDescription = caption
            },
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        BasicText(
            "Level $level",
            maxLines = 1,
            style = HudType.subheadline.copy(fontWeight = FontWeight.ExtraBold, color = Theme.xp),
        )
        val fraction = if (needed > 0) (earned.toFloat() / needed).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Theme.xp.copy(alpha = 0.25f)),
        ) {
            Box(
                Modifier.fillMaxWidth(fraction).height(4.dp).background(Theme.xp),
            )
        }
        BasicText(
            caption,
            maxLines = 1,
            style =
                HudType.caption.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.xp.copy(alpha = 0.85f),
                    fontFeatureSettings = "tnum",
                ),
        )
    }
}
