package com.hunter.stockfarmer.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.compactDurationText
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.persistence.SaveBackup
import com.hunter.stockfarmer.core.persistence.SaveSlotInfo
import com.hunter.stockfarmer.core.persistence.SaveSlots
import com.hunter.stockfarmer.game.FarmSlots
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.SavedGames
import com.hunter.stockfarmer.ui.theme.Theme
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
    session: GameSession,
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
                info,
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
            Button(
                onClick = {
                    name = SaveSlots.defaultName(empty)
                    prompt = NamePrompt.NewGame(empty)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("New Game") }
        } else if (saved != null) {
            Text("You can keep up to ${SaveSlots.COUNT} games. Delete one to start another.")
        }
        Text(
            "Your game saves by itself, and switching games saves the one you leave.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    prompt?.let { open ->
        val renaming = open is NamePrompt.Rename
        AlertDialog(
            onDismissRequest = { prompt = null },
            title = { Text(if (renaming) "Rename game" else "Name your new game") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
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
                ) { Text(if (renaming) "Save" else "Start") }
            },
            dismissButton = { TextButton(onClick = { prompt = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { info ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${info.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        farms.delete(info.slot) { refresh() }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    restoring?.let { (info, backups) ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("Restore \"${info.name}\"") },
            text = {
                Text(
                    if (backups.isEmpty()) {
                        "No backups yet. They appear as the game saves."
                    } else {
                        "The game as it is now is kept as a backup, so you can undo this."
                    },
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
                        ) { Text(backupTitle(backup)) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text("Cancel") } },
        )
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
    ScreenCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(info.name, style = MaterialTheme.typography.titleMedium)
            if (isActive) Text("Playing", color = Theme.accent)
        }
        Figure("Level", "${info.level}", Theme.gold)
        Figure("Money", info.money.dollars, Theme.accent)
        Figure("Play time", info.playTime.compactDurationText)
        Figure("Last played", relativeTime(info.lastPlayed.toEpochMilli()))
        if (!isActive) Button(onClick = onLoad, modifier = Modifier.fillMaxWidth()) { Text("Load") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRename) { Text("Rename") }
            OutlinedButton(onClick = onRestore) { Text("Restore Backup") }
            if (!isActive) OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

private fun relativeTime(epochMillis: Long) =
    DateUtils.getRelativeTimeSpanString(epochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private val BACKUP_TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun backupTitle(backup: SaveBackup) =
    backup.savedAt?.atZone(ZoneId.systemDefault())?.format(BACKUP_TIME) ?: "Unknown time"
