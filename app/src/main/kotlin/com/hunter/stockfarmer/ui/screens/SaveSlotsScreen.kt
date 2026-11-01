package com.hunter.stockfarmer.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.compactDurationText
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.persistence.SaveBackup
import com.hunter.stockfarmer.core.persistence.SaveSlotInfo
import com.hunter.stockfarmer.core.persistence.SaveSlots
import com.hunter.stockfarmer.game.FarmSlots
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.SavedGames
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.HudTextField
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the name box is for: a new game in an empty slot, or a new name for a saved one. */
private sealed interface NamePrompt {
    val slot: Int

    data class NewGame(
        override val slot: Int,
    ) : NamePrompt

    data class Rename(
        override val slot: Int,
    ) : NamePrompt
}

/** The saved games, up to ten: start a new one, or load, rename, restore or delete a saved one. */
@Composable
fun SaveSlotsScreen(
    farms: FarmSlots,
    onClose: () -> Unit,
) = SaveSlotsScreen(session = null, farms = farms, onClose = onClose)

@Composable
fun SaveSlotsScreen(
    session: GameSession?,
    farms: FarmSlots,
    onClose: () -> Unit,
) {
    var games by remember { mutableStateOf<SavedGames?>(null) }
    var prompt by remember { mutableStateOf<NamePrompt?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SaveSlotInfo?>(null) }
    var restoring by remember { mutableStateOf<Pair<SaveSlotInfo, List<SaveBackup>>?>(null) }
    val refresh = { farms.load { games = it } }
    remember { refresh() }

    ScreenLayout("Load Game", session, onClose) {
        val saved = games
        saved?.slots?.filterNotNull()?.forEach { info ->
            SlotCard(
                info = info,
                isActive = info.slot == saved.active,
                onLoad = {
                    farms.switchTo(info.slot)
                    onClose()
                },
                onRename = {
                    name = info.name
                    prompt = NamePrompt.Rename(info.slot)
                },
                onRestore = { farms.backups(info.slot) { restoring = info to it } },
                onDelete = { deleting = info },
            )
        }
        val empty = saved?.slots?.indexOfFirst { it == null } ?: -1
        if (saved != null && empty >= 0) {
            PillButton(
                title = "New Game",
                fill = Theme.accent,
                text = Theme.onAccent,
                onClick = {
                    name = SaveSlots.defaultName(empty)
                    prompt = NamePrompt.NewGame(empty)
                },
            )
        } else if (saved != null) {
            Text(
                text = "You can keep up to ${SaveSlots.COUNT} games. Delete one to start another.",
                style = HudType.subheadline,
                color = Theme.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = "Your game saves by itself, and switching games saves the one you leave.",
            style = HudType.caption,
            color = Theme.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }

    prompt?.let { open ->
        val renaming = open is NamePrompt.Rename
        HudMaterialTheme {
            AlertDialog(
                onDismissRequest = { prompt = null },
                title = {
                    Text(
                        text = if (renaming) "Rename game" else "Name your new game",
                        style = HudType.title3.copy(fontWeight = FontWeight.Bold),
                        color = Theme.textPrimary,
                    )
                },
                text = { HudTextField(name, { name = it }, "Name") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt = null
                            if (renaming) {
                                farms.rename(open.slot, name) { refresh() }
                            } else {
                                farms.startNew(open.slot, name)
                                onClose()
                            }
                        },
                    ) {
                        Text(
                            text = if (renaming) "Save" else "Start",
                            style = HudType.headline,
                            color = Theme.accent,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { prompt = null }) {
                        Text("Cancel", style = HudType.subheadline, color = Theme.textSecondary)
                    }
                },
            )
        }
    }
    deleting?.let { info ->
        HudMaterialTheme {
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = {
                    Text(
                        text = "Delete \"${info.name}\"?",
                        style = HudType.title3.copy(fontWeight = FontWeight.Bold),
                        color = Theme.textPrimary,
                    )
                },
                text = {
                    Text(
                        text = "This can't be undone.",
                        style = HudType.subheadline,
                        color = Theme.textSecondary,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleting = null
                            farms.delete(info.slot) { refresh() }
                        },
                    ) {
                        Text("Delete", style = HudType.headline, color = Theme.danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleting = null }) {
                        Text("Cancel", style = HudType.subheadline, color = Theme.textSecondary)
                    }
                },
            )
        }
    }
    restoring?.let { (info, backups) ->
        HudMaterialTheme {
            AlertDialog(
                onDismissRequest = { restoring = null },
                title = {
                    Text(
                        text = "Restore \"${info.name}\"",
                        style = HudType.title3.copy(fontWeight = FontWeight.Bold),
                        color = Theme.textPrimary,
                    )
                },
                text = {
                    Text(
                        text =
                            if (backups.isEmpty()) {
                                "No backups yet. They appear as the game saves."
                            } else {
                                "The game as it is now is kept as a backup, so you can undo this."
                            },
                        style = HudType.subheadline,
                        color = Theme.textSecondary,
                    )
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        backups.forEach { backup ->
                            TextButton(
                                onClick = {
                                    restoring = null
                                    farms.restore(backup) { refresh() }
                                },
                            ) {
                                Text(backupTitle(backup), style = HudType.subheadline, color = Theme.accent)
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { restoring = null }) {
                        Text("Cancel", style = HudType.subheadline, color = Theme.textSecondary)
                    }
                },
            )
        }
    }
}

@Composable
private fun SlotCard(
    info: SaveSlotInfo,
    isActive: Boolean,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .glassCard(16.dp, tint = if (isActive) Theme.accent else null)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = info.name,
                style = HudType.title3.copy(fontWeight = FontWeight.Bold),
                color = Theme.textPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isActive) {
                Box(
                    modifier =
                        Modifier
                            .background(Theme.accent, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Playing",
                        style = HudType.caption.copy(fontWeight = FontWeight.Bold),
                        color = Theme.onAccent,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SlotDetailRow("Level", "${info.level}", Theme.xp)
            SlotDetailRow("Money", info.money.dollars, Theme.money)
            SlotDetailRow("Play time", info.playTime.compactDurationText, Theme.textPrimary)
            SlotDetailRow("Last played", relativeTime(info.lastPlayed.toEpochMilli()), Theme.textPrimary)
        }
        if (!isActive) {
            PillButton(
                title = "Load",
                fill = Theme.accent,
                text = Theme.onAccent,
                onClick = onLoad,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SlotActionButton("Rename", Theme.textPrimary, Modifier.weight(1f), onRename)
            SlotActionButton("Restore Backup", Theme.textPrimary, Modifier.weight(1f), onRestore)
            if (!isActive) {
                SlotActionButton("Delete", Theme.danger, Modifier.weight(1f), onDelete)
            }
        }
    }
}

@Composable
private fun SlotDetailRow(
    title: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = HudType.subheadline, color = Theme.textSecondary)
        Text(value, style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = valueColor)
    }
}

@Composable
private fun SlotActionButton(
    title: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(36.dp)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            maxLines = 1,
        )
    }
}

private fun relativeTime(epochMillis: Long) =
    DateUtils.getRelativeTimeSpanString(epochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private val BACKUP_TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun backupTitle(backup: SaveBackup) =
    backup.savedAt?.atZone(ZoneId.systemDefault())?.format(BACKUP_TIME) ?: "Unknown time"
