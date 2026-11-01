package com.hunter.stockfarmer.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.screens.ScreenArt
import com.hunter.stockfarmer.ui.screens.ScreenIcon
import com.hunter.stockfarmer.ui.screens.StockMarketScreen
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import kotlinx.coroutines.delay
import java.time.Instant

enum class AnimalInteraction { PET, NUDGE }

/** The caller owns the open target. Scene-only animal gestures are forwarded without changing game rules. */
@Composable
fun SheetHost(
    session: GameSession,
    target: SheetTarget,
    onClose: () -> Unit,
    onAnimalInteraction: ((AnimalRef, AnimalInteraction) -> Unit)? = null,
) {
    var current by remember(session, target) { mutableStateOf(target) }
    var state by remember(session, current) { mutableStateOf(session.sheetState(current)) }
    var message by remember(session, current) { mutableStateOf<String?>(null) }
    var confirmation by remember(session, current) { mutableStateOf<SheetAction?>(null) }
    var stocks by remember(session, target) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(session, current, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state = session.sheetState(current)
                delay(250)
            }
        }
    }
    val act: (SheetAction) -> Unit = { action ->
        val result = session.sheetAction(current, action, Instant.now())
        message = result.refusal
        state = session.sheetState(current)
        if (result.shouldClose) {
            if (current != target) current = target else onClose()
        }
    }
    if (stocks) {
        StockMarketScreen(session, { stocks = false }, onEvents = { session.publish(it) })
        return
    }
    Dialog(
        onDismissRequest = { if (current != target) current = target else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        HudMaterialTheme {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.96f).fillMaxHeight(0.92f),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        state.building
                            ?.placed
                            ?.building
                            ?.name ?: state.animal?.name ?: "Fish",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("${state.money} · Level ${state.level}")
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.building?.let { building ->
                            BuildingSections(
                                session,
                                building,
                                act,
                                onAnimal = { current = SheetTarget.Animal(it) },
                                onStocks = { stocks = true },
                                onDemolish = { confirmation = SheetAction.Demolish },
                                onEvents = { events ->
                                    session.publish(events)
                                    message = SheetResult(events).refusal
                                },
                            )
                        }
                        state.animal?.let { animal ->
                            AnimalSection(
                                animal,
                                act,
                                onRelease = { confirmation = SheetAction.Release },
                                onInteraction =
                                    onAnimalInteraction?.let { callback ->
                                        { interaction ->
                                            callback(animal.ref, interaction)
                                            onClose()
                                        }
                                    },
                            )
                        }
                        state.fish?.let {
                            ScreenIcon(it.kind?.let { kind -> ScreenArt.image("animals/Fish", kind.assetName) })
                            Text("Use the Fishing Rod to catch fish in this pond.")
                        }
                        if (state.building == null && state.animal == null && state.fish == null) {
                            Text("This item is no longer here.")
                        }
                    }
                    message?.let {
                        Text(
                            it,
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (current != target) TextButton(onClick = { current = target }) { Text("Back") }
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                }
            }
            confirmation?.let { action ->
                SheetConfirmation(
                    if (action == SheetAction.Demolish) "Demolish this building?" else "Release this animal?",
                    if (action == SheetAction.Demolish) {
                        "${state.building?.refund.orEmpty()}\nAnimals and work in this building will be removed."
                    } else {
                        "It will leave the farm."
                    },
                    onDismiss = { confirmation = null },
                    onConfirm = {
                        confirmation = null
                        act(action)
                    },
                )
            }
        }
    }
}

private fun GameSession.sheetState(target: SheetTarget) =
    read {
        SheetState.from(GameEngine(it), target, Instant.now())
    }

@Composable
internal fun SheetCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun SheetButton(
    title: String,
    onClick: () -> Unit,
) {
    Button(onClick, Modifier.fillMaxWidth()) { Text(title) }
}

@Composable
internal fun SheetConfirmation(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
