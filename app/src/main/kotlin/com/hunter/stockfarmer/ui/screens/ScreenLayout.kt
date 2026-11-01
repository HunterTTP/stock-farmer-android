package com.hunter.stockfarmer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard
import com.hunter.stockfarmer.ui.theme.glassCard
import kotlinx.coroutines.delay

@Composable
internal fun ScreenLayout(
    title: String,
    session: GameSession? = null,
    onClose: () -> Unit,
    closeLabel: String = "Close",
    content: @Composable ColumnScope.() -> Unit,
) {
    val money = session?.let { s -> rememberScreenState(s) { s.read { it.money.dollars } }.value }.orEmpty()
    BackHandler(onBack = onClose)
    HudMaterialTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (money.isNotEmpty()) {
                        Text(money, color = Theme.accent)
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
                PillButton(closeLabel, onClick = onClose, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun ScreenCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun Figure(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = color)
    }
}

/** Read a projection while visible; nothing mutable from GameState escapes the session lock. */
@Composable
internal fun <T> rememberScreenState(
    key: Any,
    read: () -> T,
): State<T> {
    val currentRead by rememberUpdatedState(read)
    val owner = LocalLifecycleOwner.current
    return produceState(read(), key, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = currentRead()
                delay(500)
            }
        }
    }
}

@Composable
internal fun UpgradeCard(
    state: UpgradeState,
    onUpgrade: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .categoryCard(isHighlighted = false)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            state.title,
            style = HudType.title2.copy(fontWeight = FontWeight.Bold),
            color = Theme.textPrimary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.effects.forEach {
                Text(it, style = HudType.subheadline, color = Theme.textPrimary)
            }
        }
        state.lockReason?.let {
            Text(it, style = HudType.subheadline, color = Theme.spend)
        }
        val isAffordable = state.lockReason == null
        PillButton(
            title = "Upgrade · ${state.cost}",
            fill = Theme.xp,
            text = Color.White,
            enabled = isAffordable,
            modifier = Modifier.alpha(if (isAffordable) 1f else 0.4f),
            onClick = onUpgrade,
        )
    }
}

@Composable
internal fun TemporaryMessage(
    text: String?,
    onDismiss: () -> Unit,
) {
    var visible by remember(text) { mutableStateOf(text != null) }
    LaunchedEffect(text) {
        if (text != null) {
            delay(6000)
            visible = false
            onDismiss()
        }
    }
    if (visible) Text(text.orEmpty(), color = Theme.xp)
}
