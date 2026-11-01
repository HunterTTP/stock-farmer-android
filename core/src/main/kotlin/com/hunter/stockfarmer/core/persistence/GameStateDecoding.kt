package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState

internal fun GameState.finishDecoding() {
    if (holdings.values.any { it.previousCloseCents == null }) lastStockPullAt = null
}
