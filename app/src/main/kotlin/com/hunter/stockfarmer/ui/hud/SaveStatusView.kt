package com.hunter.stockfarmer.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
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
            PillButton(title = "Retry", onClick = onRetry, role = PillRole.accent, fillWidth = false)
            onChooseFarm?.let {
                PillButton(
                    title = "Choose Farm",
                    onClick = it,
                    role = PillRole.neutral,
                    fillWidth = false,
                )
            }
        }
    }
}
