package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.claimCompletedQuests
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.game.GameSession

/** The iOS commit policy turns in finished quests after each action, including stock sales. */
internal fun GameSession.screenAction(
    using: RandomSource = SystemRandomSource,
    change: (GameEngine) -> List<GameEvent>,
): List<GameEvent> = act { engine -> change(engine) + engine.claimCompletedQuests(using) }
