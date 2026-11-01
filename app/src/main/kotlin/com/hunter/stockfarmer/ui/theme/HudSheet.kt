package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The backdrop of a menu: dark glass with a faint light sweep from the top left. the wash sits
 * over a slightly lighter base because the farm cannot be blurred behind a sheet.
 */
fun Modifier.glassSheetBackground(): Modifier =
    this
        .background(Color.White.copy(alpha = 0.06f))
        .background(Color.Black.copy(alpha = Theme.GLASS_WASH))
        .background(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.White.copy(alpha = 0.04f)),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
        )

/** A tile for one category or option in a menu; the highlighted one is warmed with the accent. */
fun Modifier.categoryCard(isHighlighted: Boolean): Modifier =
    padding(14.dp).glassCard(18.dp, if (isHighlighted) Theme.accent else null)

/** A full-height menu sheet in dark glass with a visible drag handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    HudMaterialTheme {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            dragHandle = { SheetHandle() },
            content = { Column(Modifier.fillMaxSize().glassSheetBackground(), content = content) },
        )
    }
}

/** The drag indicator: a 36 by 5 point capsule. */
@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .size(width = 36.dp, height = 5.dp)
            .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(50)),
    )
}
