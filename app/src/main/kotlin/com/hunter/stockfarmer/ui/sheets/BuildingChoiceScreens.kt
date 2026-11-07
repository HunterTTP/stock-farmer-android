@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hunter.stockfarmer.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.presentation.StatKind
import com.hunter.stockfarmer.core.presentation.facts
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.hasSeen
import com.hunter.stockfarmer.core.rules.workshopRecipe
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.SheetTarget
import com.hunter.stockfarmer.ui.FAST_POLL_MILLIS
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.MenuPictures
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.rememberPolledState
import com.hunter.stockfarmer.ui.screens.ScreenIcon
import com.hunter.stockfarmer.ui.theme.BarScrollView
import com.hunter.stockfarmer.ui.theme.ChoiceRow
import com.hunter.stockfarmer.ui.theme.HudChoice
import com.hunter.stockfarmer.ui.theme.HudSheet
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.StatValuesLine
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.UnlockLine
import com.hunter.stockfarmer.ui.theme.categoryCard
import java.time.Instant

internal data class ChickChoice(
    val variant: ChickenVariant?,
    val selected: Boolean,
    val isNew: Boolean,
) {
    companion object {
        fun all(
            engine: GameEngine,
            origin: GridPoint,
        ): List<ChickChoice> {
            val selected = engine.state.coops[origin]?.nextVariantID
            return listOf(ChickChoice(null, selected == null, false)) +
                ChickenVariant.available.map {
                    ChickChoice(it, selected == it.id, !engine.hasSeen("chicken:${it.id}"))
                }
        }
    }
}

internal fun recipeChoices(
    engine: GameEngine,
    origin: GridPoint,
): List<RecipeChoice> {
    val building =
        engine.state.placedBuildings
            .firstOrNull { it.origin == origin }
            ?.building ?: return emptyList()
    return Recipe.options(building).map { recipe ->
        val locked = recipe.id != Recipe.of(building)?.id && recipe.unlockLevel > engine.state.level
        RecipeChoice(
            recipe,
            engine.state.workshopRecipe(origin)?.id == recipe.id,
            recipe.unlockLevel.takeIf { locked },
            !locked && !engine.hasSeen("recipe:${recipe.id}"),
            engine
                .facts(recipe)
                .stats
                .filter { it.kind != StatKind.unlock }
                .map { it.value },
        )
    }
}

@Composable
fun ChickAppearanceScreen(
    session: GameSession,
    origin: GridPoint,
    onDismiss: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    val choices by rememberPolledState(session to origin, FAST_POLL_MILLIS) {
        session.act { ChickChoice.all(it, origin) }
    }
    BuildingChoiceScreen("Next Chick", session, origin, onClose, onDismiss = onDismiss ?: onClose) { choose ->
        Text(
            "Choose what future chicks grow into. Existing chickens keep their appearance. " +
                "Roosters are ornamental and lay no eggs.",
            style = HudType.caption,
            color = Theme.textSecondary,
        )
        choices.forEach { choice ->
            val variant = choice.variant
            val pick = { choose(SheetAction.NextChick(variant?.id)) }
            if (variant == null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .categoryCard(choice.selected)
                            .clickable(onClick = pick)
                            .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Random Hen", style = HudType.headline, color = Theme.textPrimary)
                }
            } else {
                ChoiceRow(
                    HudChoice(
                        variant.id,
                        variant.name,
                        pick,
                        subtitle = if (variant.isRooster) "Ornamental rooster" else "Lays eggs when grown",
                        icon = { ScreenIcon(SheetPictures.chicken(variant)) },
                        isSelected = choice.selected,
                        isNew = choice.isNew,
                    ),
                    Modifier.fillMaxWidth().categoryCard(choice.selected).clickable(onClick = pick),
                )
            }
        }
    }
}

@Composable
fun WorkshopRecipeScreen(
    session: GameSession,
    origin: GridPoint,
    onDismiss: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    val choices by rememberPolledState(session to origin, FAST_POLL_MILLIS) {
        session.act { recipeChoices(it, origin) }
    }
    val loader = rememberMenuAssetLoader()
    BuildingChoiceScreen("Recipes", session, origin, onClose, onDismiss = onDismiss ?: onClose, spacing = 8) { choose ->
        choices.forEach { choice ->
            val recipe = choice.recipe
            val pick = { choose(SheetAction.Recipe(recipe.id)) }
            ChoiceRow(
                HudChoice(
                    recipe.id,
                    "${recipe.output.name} from ${recipe.input.name}",
                    pick,
                    priceLine = {
                        val level = choice.requiredLevel
                        if (level != null) UnlockLine(level) else StatValuesLine(choice.values)
                    },
                    icon = { MenuImage(MenuPictures.stored(recipe.output), loader) },
                    isSelected = choice.selected,
                    isNew = choice.isNew,
                    isLocked = choice.requiredLevel != null,
                ),
                Modifier.fillMaxWidth().categoryCard(choice.selected).clickable(onClick = pick),
            )
        }
    }
}

@Composable
private fun BuildingChoiceScreen(
    title: String,
    session: GameSession,
    origin: GridPoint,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
    spacing: Int = 12,
    content: @Composable ((SheetAction) -> Unit) -> Unit,
) {
    var message by remember(session, origin) { mutableStateOf<String?>(null) }
    val choose: (SheetAction) -> Unit = {
        val result = session.sheetAction(SheetTarget.Building(origin), it, Instant.now())
        message = result.refusal
        if (result.refusal == null) onClose()
    }
    HudSheet(onDismiss = onDismiss) {
        BackHandler(onBack = onClose)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = HudType.headline)
            PillButton(
                title = "Back",
                onClick = onClose,
                role = PillRole.neutral,
                fillWidth = false,
            )
        }
        BarScrollView(Modifier.weight(1f)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(spacing.dp)) { content(choose) }
        }
        message?.let { Text(it, Modifier.padding(20.dp), color = Theme.spend) }
    }
}
