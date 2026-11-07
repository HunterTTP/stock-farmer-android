package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.stockfarmer.core.presentation.StatKind
import com.hunter.stockfarmer.core.presentation.StatValue

/** The one color for each kind of stat, on item pages and in dropdown rows alike. */
val StatKind.color: Color
    get() =
        when (this) {
            StatKind.money -> Theme.money
            StatKind.xp, StatKind.unlock, StatKind.level -> Theme.xp
            StatKind.time -> Theme.blue
            StatKind.boost -> Theme.gold
            StatKind.count -> Theme.textPrimary
        }

/** The one symbol for each kind of stat. */
val StatKind.icon: ImageVector
    get() =
        when (this) {
            StatKind.money -> HudIcons.dollarsignCircle
            StatKind.xp -> HudIcons.sparkles
            StatKind.unlock -> HudIcons.lockFill
            StatKind.level -> HudIcons.starFill
            StatKind.time -> HudIcons.clockFill
            StatKind.boost -> HudIcons.arrowUpRight
            StatKind.count -> HudIcons.squareStackFill
        }

/** Bare stat values side by side in a dropdown row, each in its kind's color: "26m 18s / $5 / +3 XP". */
@Composable
fun StatValuesLine(
    values: List<StatValue>,
    modifier: Modifier = Modifier,
    /** Grey words before the values, such as "Unlocks at". */
    prefix: String? = null,
) {
    val scale = LocalHudScale.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        prefix?.let { StatLinePart(it, Theme.textSecondary, scale) }
        values.forEachIndexed { index, value ->
            if (index > 0) StatLinePart("/", Theme.textSecondary, scale)
            StatLinePart(value.text(compact = true), value.kind.color, scale)
        }
    }
}

@Composable
private fun StatLinePart(
    text: String,
    color: Color,
    scale: Float,
) {
    BasicText(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = HudType.footnote.copy(fontSize = (12 * scale).sp, fontWeight = FontWeight.SemiBold, color = color),
    )
}

/** A locked row's line, "Unlocks at Lv 7", with the level in its stat color. */
@Composable
fun UnlockLine(
    level: Int,
    modifier: Modifier = Modifier,
) {
    StatValuesLine(listOf(StatValue.Unlock(level)), modifier, prefix = "Unlocks at")
}
