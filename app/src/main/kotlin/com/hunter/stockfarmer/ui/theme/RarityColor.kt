package com.hunter.stockfarmer.ui.theme

import androidx.compose.ui.graphics.Color
import com.hunter.stockfarmer.core.catalog.Rarity

/** The game's color for each rarity. */
val Rarity.hudColor: Color get() = Color(color.red.toFloat(), color.green.toFloat(), color.blue.toFloat())
