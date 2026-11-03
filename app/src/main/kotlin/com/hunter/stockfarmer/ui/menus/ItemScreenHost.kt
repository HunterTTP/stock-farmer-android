@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hunter.stockfarmer.ui.menus

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.FAST_POLL_MILLIS
import com.hunter.stockfarmer.ui.rememberPolledState
import com.hunter.stockfarmer.ui.theme.HudSheet
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.MoneyBadge
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard

/** HUD entry point for seed/fertilizer/tool info and tool upgrades; catalog info buttons use this host too. */
@Composable
fun MenusHost(
    session: GameSession,
    screen: ItemScreen,
    onClose: () -> Unit,
    onSelected: () -> Unit = onClose,
    onUpgradeResult: (ToolUpgradeResult) -> Unit = {},
) {
    var message by remember(session, screen) { mutableStateOf<String?>(null) }
    val money by rememberPolledState(session to screen, FAST_POLL_MILLIS) { session.read { it.money } }
    HudSheet(onDismiss = onClose) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (screen) {
                    is ItemScreen.Item -> {
                        session.act { ItemInfo.from(screen.item, it).name }
                    }

                    ItemScreen.Eraser -> {
                        "Grass"
                    }

                    is ItemScreen.Harvest -> {
                        screen.tool.displayName
                    }

                    is ItemScreen.Upgrade -> {
                        "Upgrade ${screen.tool.displayName}"
                    }
                },
                Modifier.weight(1f),
                style = HudType.headline,
                color = Theme.textPrimary,
            )
            MoneyBadge(money)
            Box(
                modifier =
                    Modifier
                        .padding(start = 12.dp)
                        .glassCard(12.dp)
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Close",
                    style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = Theme.textPrimary,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            when (screen) {
                is ItemScreen.Item -> {
                    ItemDetailScreen(session, screen.item, onSelected, { message = it })
                }

                ItemScreen.Eraser -> {
                    EraserDetailScreen(session, onSelected)
                }

                is ItemScreen.Harvest -> {
                    HarvestToolDetailScreen(session, screen.tool, onSelected)
                }

                is ItemScreen.Upgrade -> {
                    ToolUpgradeScreen(session, screen.tool, onBought = {
                        onUpgradeResult(ToolUpgradeResult(true, it))
                        onClose()
                    }, onMessage = {
                        message = it
                        onUpgradeResult(ToolUpgradeResult(false, it))
                    })
                }
            }
        }
        message?.let { MenuMessageBox(it, isRejection = true) }
    }
}
