@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hunter.stockfarmer.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.menus.Menu
import com.hunter.stockfarmer.ui.menus.MenusHost
import com.hunter.stockfarmer.ui.screens.BuildingUpgradeScreen
import com.hunter.stockfarmer.ui.screens.ScreenArt
import com.hunter.stockfarmer.ui.screens.ScreenIcon
import com.hunter.stockfarmer.ui.screens.StockMarketScreen
import com.hunter.stockfarmer.ui.theme.BarScrollView
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.HudSheet
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard
import kotlinx.coroutines.delay
import java.time.Instant

enum class AnimalInteraction { PET, NUDGE }

private enum class BuildingPage { CHICK, RECIPES, UPGRADE }

/** Scene-only petting and nudging go to the caller; all game actions go through GameSession.act. */
@Composable
fun SheetHost(
    session: GameSession,
    target: SheetTarget,
    onClose: () -> Unit,
    onAnimalInteraction: ((AnimalRef, AnimalInteraction) -> Unit)? = null,
) {
    var state by remember(session, target) { mutableStateOf(session.sheetState(target)) }
    var message by remember(session, target) { mutableStateOf<String?>(null) }
    var page by remember(session, target) { mutableStateOf<BuildingPage?>(null) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(session, target, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state = session.sheetState(target)
                delay(250)
            }
        }
    }
    val act: (SheetAction) -> Unit = { action ->
        val result = session.sheetAction(target, action, Instant.now())
        message = result.refusal
        state = session.sheetState(target)
        if (result.shouldClose) onClose()
    }
    state.building?.let { building ->
        val origin = building.placed.origin
        when (page) {
            BuildingPage.CHICK -> {
                ChickAppearanceScreen(session, origin) { page = null }
                return
            }

            BuildingPage.RECIPES -> {
                WorkshopRecipeScreen(session, origin) { page = null }
                return
            }

            BuildingPage.UPGRADE -> {
                BuildingUpgradeScreen(session, { page = null }, origin, { session.publish(it) })
                return
            }

            null -> {
                Unit
            }
        }
        if (building.placed.building.isHouse) {
            StockMarketScreen(session, onClose, onEvents = { session.publish(it) })
            return
        }
    }
    state.animal?.let { animal ->
        HudMaterialTheme {
            AnimalMenu(
                animal,
                act,
                onClose,
                message,
                onInteraction =
                    onAnimalInteraction?.let { callback ->
                        { interaction ->
                            callback(animal.ref, interaction)
                            onClose()
                        }
                    },
            )
        }
        return
    }
    HudSheet(onDismiss = onClose) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.building
                    ?.placed
                    ?.building
                    ?.name ?: "Fish",
                Modifier.weight(1f),
                style = HudType.headline,
            )
            TextButton(onClose) { Text("Close") }
        }
        BarScrollView(Modifier.weight(1f)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                state.building?.let { building ->
                    BuildingSections(
                        session,
                        building,
                        act,
                        onChick = { page = BuildingPage.CHICK },
                        onRecipes = { page = BuildingPage.RECIPES },
                        onUpgrade = { page = BuildingPage.UPGRADE },
                        onEvents = { events ->
                            session.publish(events)
                            message = SheetResult(events).refusal
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
        }
        message?.let {
            Text(it, Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }, color = Theme.spend)
        }
    }
}

private fun GameSession.sheetState(target: SheetTarget) =
    read {
        SheetState.from(GameEngine(it), target, Instant.now())
    }

@Composable
internal fun SheetCard(
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().categoryCard(highlighted),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
internal fun SheetButton(
    title: String,
    onClick: () -> Unit,
) {
    Button(onClick, Modifier.fillMaxWidth()) { Text(title) }
}
