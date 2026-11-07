package com.hunter.stockfarmer.ui.menus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.presentation.ItemFacts
import com.hunter.stockfarmer.core.presentation.ItemStat
import com.hunter.stockfarmer.ui.theme.EffectsCard
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.color
import com.hunter.stockfarmer.ui.theme.glassCard
import com.hunter.stockfarmer.ui.theme.icon

/** An item's stat card, one row for each of its numbers, and under it the card of its notes. */
@Composable
fun FactsCards(facts: ItemFacts) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (facts.stats.isNotEmpty()) StatList(facts.stats)
        if (facts.notes.isNotEmpty()) EffectsCard(facts.notes)
    }
}

/** A card of stat rows: the kind's symbol, a short label, and the value in the kind's color. */
@Composable
fun StatList(stats: List<ItemStat>) {
    Column(
        Modifier.fillMaxWidth().glassCard(14.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stats.forEach { StatRow(it) }
    }
}

@Composable
private fun StatRow(stat: ItemStat) {
    val style = HudType.subheadline
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { StatSymbol(stat) }
        BasicText(
            stat.label,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = style.copy(color = Theme.textSecondary),
        )
        stat.tool?.let { HarvestToolImage(it, Modifier.size(22.dp)) }
        BasicText(
            stat.value.text(compact = false),
            maxLines = 1,
            style = style.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", color = stat.kind.color),
        )
        stat.unit?.let { BasicText(it, maxLines = 1, style = style.copy(color = Theme.textSecondary)) }
    }
}

/** The picture of the item a stat is about, or else its kind's symbol. */
@Composable
private fun StatSymbol(stat: ItemStat) {
    val item = stat.item
    if (item != null) {
        MenuImage(MenuPictures.stored(item), rememberMenuAssetLoader(), Modifier.size(24.dp))
    } else {
        HudIcon(stat.kind.icon, stat.kind.color, size = 16.dp)
    }
}
