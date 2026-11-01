@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hunter.stockfarmer.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.durationText
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.hasSeen
import com.hunter.stockfarmer.core.rules.workshopRecipe
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.MenuPictures
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.screens.ScreenIcon
import com.hunter.stockfarmer.ui.theme.BarScrollView
import com.hunter.stockfarmer.ui.theme.ChoiceRow
import com.hunter.stockfarmer.ui.theme.HudChoice
import com.hunter.stockfarmer.ui.theme.HudSheet
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard
import kotlinx.coroutines.delay
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
        )
    }
}

@Composable
fun ChickAppearanceScreen(
    session: GameSession,
    origin: GridPoint,
    onClose: () -> Unit,
) {
    var choices by remember(session, origin) { mutableStateOf(session.act { ChickChoice.all(it, origin) }) }
    LaunchedEffect(session, origin) {
        while (true) {
            choices = session.act { ChickChoice.all(it, origin) }
            delay(250)
        }
    }
    BuildingChoiceScreen("Next Chick", session, origin, onClose) { choose ->
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
                TextButton(pick, Modifier.fillMaxWidth().categoryCard(choice.selected)) {
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
    onClose: () -> Unit,
) {
    var choices by remember(session, origin) { mutableStateOf(session.act { recipeChoices(it, origin) }) }
    val loader = rememberMenuAssetLoader()
    LaunchedEffect(session, origin) {
        while (true) {
            choices = session.act { recipeChoices(it, origin) }
            delay(250)
        }
    }
    BuildingChoiceScreen("Recipes", session, origin, onClose) { choose ->
        choices.forEach { choice ->
            val recipe = choice.recipe
            val pick = { choose(SheetAction.Recipe(recipe.id)) }
            ChoiceRow(
                HudChoice(
                    recipe.id,
                    "${recipe.output.name} from ${recipe.input.name}",
                    pick,
                    subtitle =
                        choice.requiredLevel?.let { "Unlocks at Level $it" }
                            ?: "${recipe.inputCount} → ${recipe.outputCount} · ${recipe.duration.durationText}",
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
    content: @Composable ((SheetAction) -> Unit) -> Unit,
) {
    var message by remember(session, origin) { mutableStateOf<String?>(null) }
    val choose: (SheetAction) -> Unit = {
        val result = session.sheetAction(SheetTarget.Building(origin), it, Instant.now())
        message = result.refusal
        if (result.refusal == null) onClose()
    }
    HudSheet(onDismiss = onClose) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(title, Modifier.weight(1f), style = HudType.headline)
            TextButton(onClose) { Text("Back") }
        }
        BarScrollView(Modifier.weight(1f)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content(choose) }
        }
        message?.let { Text(it, Modifier.padding(20.dp), color = Theme.spend) }
    }
}
