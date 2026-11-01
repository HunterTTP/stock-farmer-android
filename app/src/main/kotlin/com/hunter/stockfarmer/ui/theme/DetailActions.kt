package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.dollars

/** The green button at the bottom of an item's page that makes it the active item. */
@Composable
fun SelectButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(Theme.accent, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Select", style = HudType.headline.copy(color = Theme.onAccent))
    }
}

/** Every panel where you spend or earn shows your total money. */
@Composable
fun MoneyBadge(
    money: Double,
    modifier: Modifier = Modifier,
) {
    BasicText(
        money.dollars,
        modifier.semantics { contentDescription = "Money ${money.dollars}" },
        style = HudType.subheadline.copy(fontWeight = FontWeight.Bold, color = Theme.money),
    )
}

@Composable
fun LevelRequirementNotice(
    level: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(Theme.xp.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudIcon(HudIcons.lockFill, Theme.xp, size = 17.dp)
        BasicText("Unlocks at Level $level", style = HudType.headline.copy(color = Theme.xp))
    }
}
