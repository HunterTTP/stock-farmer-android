package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The iOS HUD palette, value for value (SwiftUI `Color(red:green:blue:)` and `Color(white:)`). */
object Theme {
    val launchBackground = Color(36 / 255f, 36 / 255f, 36 / 255f)
    val accent = Color(0.53f, 1f, 0.52f)

    /** The glow of whatever the first-time walk through points at. */
    val gold = Color(1f, 0.8f, 0.2f)
    val panelBorder = Color(0.31f, 0.31f, 0.31f, 0.6f)

    /** How much black is washed over every glass panel and menu, which sets how little of the farm shows through. */
    const val GLASS_WASH = 0.5f

    /** The faint fill of a tile inside a glass menu. */
    val glassFill = Color.White.copy(alpha = 0.09f)

    /** A hairline of light that is brightest at the top left, where a glass edge catches it. */
    val glassEdge =
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.14f)),
        )
    val textPrimary = Color(0.88f, 0.88f, 0.88f)
    val textSecondary = Color(0.65f, 0.65f, 0.65f)
    val blue = Color(0.36f, 0.66f, 1f)
    val xp = Color(0.66f, 0.45f, 0.98f)
    val money = accent
    val earn = accent

    /** A tool's level text at level 3 and above; level 1 is white, level 2 purple. */
    val toolLevel3 = Color(1f, 0.58f, 0.16f)
    val spend = Color(1f, 0.35f, 0.35f)
    val danger = Color(0.85f, 0.25f, 0.25f)

    /** The text on a filled button of each color: dark green on green, dark red on red and white on grey. */
    val onAccent = Color(0.03f, 0.24f, 0.06f)
    val onDanger = Color(0.30f, 0.02f, 0.02f)
    val onNeutral = Color.White

    fun toolLevel(level: Int): Color =
        when {
            level <= 1 -> textPrimary
            level == 2 -> xp
            else -> toolLevel3
        }
}

/** SwiftUI's default text styles on iOS, in sp. The system rounded design has no Android equivalent; sans-serif stands in. */
object HudType {
    private fun style(
        size: Int,
        weight: FontWeight = FontWeight.Normal,
    ) = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, fontWeight = weight)

    val caption = style(12)
    val footnote = style(13)
    val body = style(17)
    val subheadline = style(15)
    val headline = style(17, FontWeight.SemiBold)
    val title3 = style(20)
    val title2 = style(22)
    val title = style(28)

    /** `.system(.title, design: .rounded, weight: .heavy)` for quantities. */
    val quantity = title.copy(fontWeight = FontWeight.ExtraBold)
}

/**
 * Dark glass for every panel that floats over the farm: one recipe so they all match. The iOS blur of what is behind has
 * no cheap Android equivalent over a libGDX surface, so a faint light base under the wash stands in for the material.
 */
fun Modifier.darkGlass(shape: Shape): Modifier =
    this
        .background(Color.White.copy(alpha = 0.06f), shape)
        .background(Color.Black.copy(alpha = Theme.GLASS_WASH), shape)
        .border(1.dp, Theme.glassEdge, shape)

fun Modifier.hudPanel(cornerRadius: Dp = 16.dp): Modifier = darkGlass(RoundedCornerShape(cornerRadius))

/** A frosted tile inside a menu: a faint fill with a hairline of light, warmed by [tint] (the green of a selected row). */
fun Modifier.glassCard(
    cornerRadius: Dp = 14.dp,
    tint: Color? = null,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val filled = background(tint?.copy(alpha = 0.16f) ?: Theme.glassFill, shape)
    return if (tint == null) {
        filled.border(1.dp, Theme.glassEdge, shape)
    } else {
        filled.border(1.5.dp, tint.copy(alpha = 0.9f), shape)
    }
}

/** The rounded square that option tiles sit on. */
fun Modifier.menuSquare(isSelected: Boolean = false): Modifier =
    glassCard(18.dp, if (isSelected) Theme.accent else null)

/** The Material colors every screen draws with, taken from [Theme]. */
@Composable
fun HudMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Theme.accent,
                onPrimary = Theme.onAccent,
                secondary = Theme.xp,
                background = Theme.launchBackground,
                surface = Color(0.19f, 0.19f, 0.19f),
                onSurface = Theme.textPrimary,
                onSurfaceVariant = Theme.textSecondary,
                error = Theme.spend,
                onError = Theme.onDanger,
            ),
        content = content,
    )
}
