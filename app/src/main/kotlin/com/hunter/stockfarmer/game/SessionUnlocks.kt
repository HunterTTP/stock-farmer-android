package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.rules.UnlockCategory
import com.hunter.stockfarmer.core.rules.hasNewItems

fun GameSession.hasNewItems(category: UnlockCategory): Boolean = act { it.hasNewItems(category) }
