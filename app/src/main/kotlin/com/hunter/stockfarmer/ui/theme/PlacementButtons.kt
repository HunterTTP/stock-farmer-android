package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.stockfarmer.core.model.dollars

/**
 * The buttons shown while something is being placed, as words: Place, then Destroy when it is one already standing that is being
 * moved, then Cancel. [cost] is what Place will charge, or null when it is free.
 */
@Composable
fun PlacementButtons(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cost: Double? = null,
    onDestroy: (() -> Unit)? = null,
) {
    Row(modifier.hudPanel(22.dp).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlacementButton("Place", Theme.accent, Theme.onAccent, cost, onConfirm)
        onDestroy?.let { PlacementButton("Destroy", Theme.danger, Theme.onDanger, null, it) }
        PlacementButton("Cancel", Theme.textSecondary, Theme.onNeutral, null, onCancel)
    }
}

@Composable
private fun RowScope.PlacementButton(
    title: String,
    color: Color,
    foreground: Color,
    cost: Double?,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 52.dp)
            .background(color, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = cost?.let { "$title, ${it.dollars}" } ?: title }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            cost?.let { "$title · ${it.dollars}" } ?: title,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp),
            style = HudType.title3.copy(fontWeight = FontWeight.Bold, color = foreground),
        )
    }
}
