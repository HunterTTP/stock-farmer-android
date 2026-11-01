package com.hunter.stockfarmer.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.hudPanel

/** What the game says when saving fails: the problem, a Retry and, where a farm can be chosen instead, Choose Farm. */
@Composable
fun SaveStatusView(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onChooseFarm: (() -> Unit)? = null,
) {
    Column(
        modifier.hudPanel(16.dp).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            message ?: "Saving is unavailable.",
            style = HudType.body.copy(color = Theme.textPrimary, textAlign = TextAlign.Center),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaveButton("Retry", onRetry)
            onChooseFarm?.let { SaveButton("Choose Farm", it) }
        }
    }
}

@Composable
private fun SaveButton(
    title: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .background(Theme.accent, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(title, style = HudType.body.copy(fontWeight = FontWeight.SemiBold, color = Theme.onAccent)) }
}
