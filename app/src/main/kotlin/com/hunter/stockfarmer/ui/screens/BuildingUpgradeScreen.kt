package com.hunter.stockfarmer.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.upgradeHome
import com.hunter.stockfarmer.core.rules.upgradeMarketStand
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.game.GameSession
import java.time.Instant

@Composable
fun BuildingUpgradeScreen(
    session: GameSession,
    onClose: () -> Unit,
    origin: GridPoint? = null,
    onEvents: (List<GameEvent>) -> Unit = {},
) {
    val buildings by rememberScreenState(session) { session.act { BuildingUpgradeState.all(it, Instant.now()) } }
    var message by remember { mutableStateOf<String?>(null) }
    ScreenLayout("Upgrade", session, onClose) {
        val offered = buildings.filter { origin == null || it.origin == origin }
        if (offered.isEmpty()) Text("No building upgrades are available here.")
        message?.let { Text(it) }
        offered.forEach { building ->
            UpgradeCard(building.upgrade) {
                val events =
                    session.screenAction {
                        if (building.market) {
                            it.upgradeMarketStand(building.origin, Instant.now(), SystemRandomSource)
                        } else {
                            it.upgradeHome(building.origin)
                        }
                    }
                onEvents(events)
                message =
                    events
                        .filterIsInstance<GameEvent.Rejected>()
                        .firstOrNull()
                        ?.rejection
                        ?.message
                if (message == null) onClose()
            }
        }
    }
}
