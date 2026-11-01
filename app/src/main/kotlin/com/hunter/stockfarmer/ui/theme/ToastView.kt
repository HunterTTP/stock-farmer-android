package com.hunter.stockfarmer.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A snappy spring: 0.5 second spring with 0.15 bounce, so a damping ratio of 0.85 and stiffness (2 pi / 0.5) squared. */
object ToastMotion {
    const val DAMPING_RATIO = 0.85f
    const val STIFFNESS = 157.9f
    const val ENTER_SCALE = 0.9f
}

/**
 * A message that fades and scales in, over [message] being null for no toast. [tool] is an implement the message asks for, drawn
 * by [toolIcon] as a button that picks it up through [onToolTap].
 */
@Composable
fun ToastView(
    message: String?,
    modifier: Modifier = Modifier,
    toolName: String? = null,
    toolIcon: (@Composable () -> Unit)? = null,
    onToolTap: () -> Unit = {},
) {
    val spring = spring<Float>(ToastMotion.DAMPING_RATIO, ToastMotion.STIFFNESS)
    AnimatedContent(
        message,
        modifier,
        transitionSpec = {
            ContentTransform(
                fadeIn(spring) + scaleIn(spring, ToastMotion.ENTER_SCALE),
                fadeOut(spring) + scaleOut(spring, ToastMotion.ENTER_SCALE),
            )
        },
        contentAlignment = Alignment.Center,
        label = "toast",
    ) { shown ->
        if (shown != null) {
            Row(
                Modifier.hudPanel(12.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    shown,
                    style =
                        HudType.subheadline.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Theme.textPrimary,
                            textAlign = TextAlign.Center,
                        ),
                )
                if (toolIcon != null) ToolButton(toolName, toolIcon, onToolTap)
            }
        }
    }
}

@Composable
private fun ToolButton(
    toolName: String?,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .background(Theme.accent.copy(alpha = 0.18f), shape)
            .border(1.dp, Theme.accent, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Pick up the ${toolName.orEmpty()}" }
            .padding(4.dp),
    ) {
        Box(Modifier.size(36.dp)) { icon() }
    }
}
