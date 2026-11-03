package com.hunter.stockfarmer.ui.screens

import androidx.compose.runtime.Composable
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.game.GameSession

/** Can also be embedded in the building menu for a particular stand. */
@Composable
fun MarketQuestSection(
    session: GameSession,
    origin: GridPoint,
    onEvents: (List<GameEvent>) -> Unit = {},
) = com.hunter.stockfarmer.ui.sheets
    .MarketQuestSection(session, origin, onEvents)
