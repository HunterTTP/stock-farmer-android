package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dark glass for panels floating over the farm.
 * Synonym for [hudPanel].
 */
fun Modifier.glassPanel(cornerRadius: Dp = 16.dp): Modifier = hudPanel(cornerRadius)

/**
 * A floating glass panel container over the farm.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glassPanel(cornerRadius),
        content = content,
    )
}
