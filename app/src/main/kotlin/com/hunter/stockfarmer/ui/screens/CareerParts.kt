package com.hunter.stockfarmer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard

/** A card a quarter of the page wide, with a title, whatever is at its middle and a line beneath. */
@Composable
internal fun SquareCard(
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
    opensPage: Boolean = false,
    onClick: (() -> Unit)? = null,
    caption: String? = null,
    middle: @Composable () -> Unit,
) {
    val tap = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Column(
        modifier
            .aspectRatio(1f)
            .glassCard(18.dp)
            .then(tap)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                title,
                Modifier.weight(1f),
                color = Theme.textSecondary,
                style = HudType.footnote.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            if (opensPage) Text("›", color = Theme.textSecondary, style = HudType.footnote)
        }
        middle()
        Text(
            caption.orEmpty(),
            color = tint.copy(alpha = 0.9f),
            style = HudType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

@Composable
internal fun SquareValue(
    text: String,
    color: Color,
) = Text(text, color = color, style = HudType.title2.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)

@Composable
internal fun LevelRing(
    level: Int,
    fraction: Float,
    isMax: Boolean,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Theme.xp.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(
                Theme.xp,
                -90f,
                360f * if (isMax) 1f else fraction.coerceIn(0f, 1f),
                false,
                Offset(inset, inset),
                arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text("$level", color = Theme.xp, style = HudType.title.copy(fontWeight = FontWeight.ExtraBold))
    }
}

@Composable
internal fun CollectionRow(
    collection: CareerCollection,
    collected: Int,
    total: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassCard(18.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(collection.title, Modifier.weight(1f), color = Theme.textPrimary, style = HudType.headline)
                Text(
                    "$collected / $total",
                    color = if (collected == total) Theme.accent else Theme.textSecondary,
                    style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            ProgressBar(collected, total, Theme.accent)
        }
        Text("›", color = Theme.textSecondary, style = HudType.headline)
    }
}

@Composable
internal fun ProgressBar(
    collected: Int,
    total: Int,
    tint: Color,
) = LinearProgressIndicator(
    progress = { collected.toFloat() / total.coerceAtLeast(1) },
    modifier = Modifier.fillMaxWidth(),
    color = tint,
    trackColor = tint.copy(alpha = 0.2f),
)

@Composable
internal fun PerfectionCard(
    collected: Int,
    total: Int,
    percent: Int,
) {
    Column(
        Modifier.fillMaxWidth().glassCard(18.dp, Theme.gold).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Perfection", Modifier.weight(1f), color = Theme.gold, style = HudType.headline)
            Text("$percent%", color = Theme.gold, style = HudType.title.copy(fontWeight = FontWeight.ExtraBold))
        }
        ProgressBar(collected, total, Theme.gold)
        Text(
            "${ScreenFormat.number(collected.toLong())} of ${ScreenFormat.number(total.toLong())} completed",
            color = Theme.textSecondary,
            style = HudType.footnote,
        )
    }
}

@Composable
internal fun StatsCard(rows: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().glassCard(18.dp).padding(horizontal = 16.dp)) {
        rows.forEachIndexed { index, (title, value) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.glassFill))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(title, Modifier.weight(1f), color = Theme.textSecondary, style = HudType.subheadline)
                Text(
                    value,
                    color = Theme.textPrimary,
                    style = HudType.subheadline.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
internal fun CareerTile(
    entry: CareerEntry,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .glassCard(14.dp)
            .alpha(if (entry.collected) 1f else 0.7f)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            ScreenIcon(entry.art, entry.symbol, entry.collected)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.name,
                color = if (entry.collected) Theme.textPrimary else Theme.textSecondary,
                style = HudType.footnote.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
            )
            val caption = entry.detail
            if (entry.collected && caption != null) {
                Text(caption, color = Theme.accent, style = HudType.caption.copy(fontSize = 11.sp), maxLines = 1)
            }
        }
        if (entry.collected) Text("✓", color = Theme.accent, style = HudType.subheadline)
    }
}
