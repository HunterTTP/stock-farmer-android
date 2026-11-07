package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

/** A count shown under an item's picture. */
data class DetailStat(
    val title: String,
    val value: Int,
) {
    val display: String get() = NumberFormat.getIntegerInstance(Locale.US).format(value)
}

/** One thing placing an item takes, shown as a square under its picture. */
data class Requirement(
    val id: String,
    val label: String,
    val value: AnnotatedString,
    val icon: @Composable () -> Unit,
)

/** A card of an item's notes: at most two short plain bullets, for what its stats can't say. */
@Composable
fun EffectsCard(
    notes: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().glassCard(14.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        notes.forEach { note ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HudIcon(HudIcons.sparkle, Theme.textPrimary, size = 15.dp)
                BasicText(note, style = HudType.subheadline.copy(color = Theme.textPrimary))
            }
        }
    }
}

/**
 * Icon, name, stats, notes and counts, with the panel's actions underneath. [facts] draws the stat and note cards. When
 * [onSelect] is set a Select button is pinned to the bottom of the panel.
 */
@Composable
fun DetailLayout(
    name: String,
    facts: @Composable () -> Unit,
    stats: List<DetailStat>,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    dropdowns: (@Composable () -> Unit)? = null,
    requirements: List<Requirement> = emptyList(),
    onSelect: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        BarScrollView(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.fillMaxWidth().categoryCard(isHighlighted),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.foundation.layout
                        .Box(Modifier.size(96.dp)) { icon() }
                    BasicText(
                        name,
                        style = HudType.title2.copy(fontWeight = FontWeight.Bold, color = Theme.textPrimary),
                    )
                }
                dropdowns?.invoke()
                if (requirements.isNotEmpty()) RequirementTiles(requirements)
                facts()
                if (stats.isNotEmpty()) StatTiles(stats)
                actions()
            }
        }
        onSelect?.let { SelectButton(it) }
    }
}

@Composable
private fun RequirementTiles(requirements: List<Requirement>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        requirements.forEach { requirement ->
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 110.dp)
                    .menuSquare()
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.layout
                    .Box(Modifier.size(44.dp)) { requirement.icon() }
                BasicText(
                    requirement.value,
                    style = HudType.title3.copy(fontWeight = FontWeight.ExtraBold, color = Color.White),
                )
                BasicText(requirement.label, style = HudType.caption.copy(color = Theme.textSecondary))
            }
        }
    }
}

@Composable
private fun StatTiles(stats: List<DetailStat>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.forEach { stat ->
            Column(
                Modifier
                    .weight(1f)
                    .glassCard(14.dp)
                    .padding(vertical = 12.dp)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    stat.display,
                    style =
                        HudType.title2.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Theme.textPrimary,
                            textAlign = TextAlign.Center,
                        ),
                )
                BasicText(stat.title, style = HudType.caption.copy(color = Theme.textSecondary))
            }
        }
    }
}
