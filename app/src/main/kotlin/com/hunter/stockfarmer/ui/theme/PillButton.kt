@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
        PillRole.earn -> Theme.accent
        PillRole.accent -> Theme.accent
        PillRole.destructive -> Theme.danger
        PillRole.neutral -> Theme.glassFill
    }

fun pillContentColor(role: PillRole): Color =
    when (role) {
        PillRole.earn -> Theme.onAccent
        PillRole.accent -> Theme.onAccent
        PillRole.destructive -> Theme.onDanger
        PillRole.neutral -> Theme.textPrimary
    }

fun pillBorderStroke(role: PillRole): BorderStroke? =
    when (role) {
        PillRole.neutral -> BorderStroke(1.dp, Theme.glassEdge)
        PillRole.destructive, PillRole.accent, PillRole.earn -> null
    }
