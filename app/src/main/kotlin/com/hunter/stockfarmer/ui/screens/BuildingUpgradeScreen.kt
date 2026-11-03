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
import com.hunter.stockfarmer.core.rules.upgradeHouse
import com.hunter.stockfarmer.core.rules.upgradeMarketStand
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.rememberPolledState
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import java.time.Instant

@Composable
fun BuildingUpgradeScreen(
    session: GameSession,
    onClose: () -> Unit,
    origin: GridPoint? = null,
    onEvents: (List<GameEvent>) -> Unit = {},
) {
    val buildings by rememberPolledState(session) { session.act { BuildingUpgradeState.all(it, Instant.now()) } }
    var message by remember { mutableStateOf<String?>(null) }
    ScreenLayout("Upgrade", session, onClose) {
        val offered = buildings.filter { origin == null || it.origin == origin }
        if (offered.isEmpty()) {
            Text("No building upgrades are available here.", style = HudType.subheadline, color = Theme.textSecondary)
        }
        message?.let { Text(it, style = HudType.subheadline, color = Theme.spend) }
        offered.forEach { building ->
            UpgradeCard(building.upgrade) {
                val events =
                    session
                        .act {
                            when (building.kind) {
                                BuildingUpgradeKind.MARKET -> {
                                    it.upgradeMarketStand(building.origin, Instant.now(), SystemRandomSource)
                                }

                                BuildingUpgradeKind.HOUSE -> {
                                    it.upgradeHouse(building.origin, Instant.now())
                                }

                                BuildingUpgradeKind.HOME -> {
                                    it.upgradeHome(building.origin)
                                }
                            }
                        }.let(session::publish)
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
