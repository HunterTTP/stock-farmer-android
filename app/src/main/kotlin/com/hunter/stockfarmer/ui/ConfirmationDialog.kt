package com.hunter.stockfarmer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.game.Confirmation
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.Theme

/** This branch uses the launch background as the fill for HUD dialogs. */
private val Theme.panelFill get() = launchBackground

/** The question the game is asking, with its confirm, optional alternative and Cancel; every button closes it. */
@Composable
fun ConfirmationDialog(
    session: GameSession,
    asked: Confirmation,
) {
    AlertDialog(
        onDismissRequest = { session.confirmations.value = null },
        containerColor = Theme.panelFill,
        titleContentColor = Theme.textPrimary,
        textContentColor = Theme.textSecondary,
        title = { Text(asked.title) },
        text = { Text(asked.message) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                asked.alternative?.let { alt ->
                    PillButton(
                        title = alt.title,
                        onClick = {
                            session.confirmations.value = null
                            alt.action()
                        },
                    )
                }
                PillButton(
                    role = PillRole.accent,
                    onClick = {
                        session.confirmations.value = null
                        asked.action()
                    },
                ) { Text(asked.confirmTitle, color = Theme.onAccent) }
            }
        },
        dismissButton = {
            PillButton(title = "Cancel", onClick = { session.confirmations.value = null })
        },
    )
}
