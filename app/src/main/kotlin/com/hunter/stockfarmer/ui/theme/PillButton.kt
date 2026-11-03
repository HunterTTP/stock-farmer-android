@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class PillRole {
    earn,
    accent,
    destructive,
    neutral,
    ACCENT,
    DESTRUCTIVE,
    NEUTRAL,
    ;

    val fill: Color
        get() =
            when (this) {
                earn, accent, ACCENT -> Theme.accent
                destructive, DESTRUCTIVE -> Theme.danger
                neutral, NEUTRAL -> Color.White.copy(alpha = 0.16f)
            }

    val text: Color
        get() =
            when (this) {
                earn, accent, ACCENT -> Theme.onAccent
                destructive, DESTRUCTIVE -> Theme.onDanger
                neutral, NEUTRAL -> Theme.onNeutral
            }

    companion object {
        val accent: PillRole = ACCENT
        val destructive: PillRole = DESTRUCTIVE
        val neutral: PillRole = NEUTRAL
    }
}

@Composable
fun PillButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: PillRole = PillRole.neutral,
    enabled: Boolean = true,
    cornerRadius: Dp = 14.dp,
    icon: (@Composable () -> Unit)? = null,
) {
    PillButton(
        onClick = onClick,
        modifier = modifier,
        role = role,
        enabled = enabled,
        cornerRadius = cornerRadius,
    ) {
        if (icon != null) {
            icon()
        }
        BasicText(
            text = title,
            style =
                HudType.headline.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = pillContentColor(role),
                ),
        )
    }
}

@Composable
fun PillButton(
    title: String,
    role: PillRole,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    onClick: () -> Unit,
) {
    PillButton(
        title = title,
        fill = role.fill,
        text = role.text,
        modifier = modifier,
        enabled = enabled,
        fillWidth = fillWidth,
        onClick = onClick,
    )
}

@Composable
fun PillButton(
    title: String,
    fill: Color,
    text: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 16.dp,
    fillWidth: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 52.dp)
            .then(modifier)
            .alpha(if (enabled) 1f else 0.4f)
            .background(fill, RoundedCornerShape(cornerRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = text,
            style = HudType.headline.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: PillRole = PillRole.neutral,
    enabled: Boolean = true,
    cornerRadius: Dp = 14.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val containerColor = pillContainerColor(role)
    val borderStroke = pillBorderStroke(role)

    val buttonModifier =
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .then(if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier)
            .background(containerColor, shape)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier.alpha(0.4f)
                },
            ).padding(contentPadding)

    Row(
        modifier = buttonModifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

fun pillContainerColor(role: PillRole): Color =
    when (role) {
        PillRole.earn, PillRole.accent, PillRole.ACCENT -> Theme.accent
        PillRole.destructive, PillRole.DESTRUCTIVE -> Theme.danger
        PillRole.neutral, PillRole.NEUTRAL -> Theme.glassFill
    }

fun pillContentColor(role: PillRole): Color =
    when (role) {
        PillRole.earn, PillRole.accent, PillRole.ACCENT -> Theme.onAccent
        PillRole.destructive, PillRole.DESTRUCTIVE -> Theme.onDanger
        PillRole.neutral, PillRole.NEUTRAL -> Theme.textPrimary
    }

fun pillBorderStroke(role: PillRole): BorderStroke? =
    when (role) {
        PillRole.neutral, PillRole.NEUTRAL -> BorderStroke(1.dp, Theme.glassEdge)
        PillRole.destructive, PillRole.DESTRUCTIVE, PillRole.accent, PillRole.ACCENT, PillRole.earn -> null
    }
