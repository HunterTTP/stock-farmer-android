package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** The arithmetic behind [QuantityControls]: a quantity is always between 1 and the maximum (or 1 when nothing is on offer). */
object QuantityRange {
    fun fewer(quantity: Int): Int = max(1, quantity - 1)

    fun more(
        quantity: Int,
        maximum: Int,
    ): Int = min(maximum, quantity + 1)

    fun max(maximum: Int): Int = max(1, maximum)

    fun canDecrease(quantity: Int): Boolean = quantity > 1

    fun canIncrease(
        quantity: Int,
        maximum: Int,
    ): Boolean = quantity < maximum
}

/** Minus, the quantity, plus and Max, with a slider under them, for picking how many of something from 1 up to a maximum. */
@Composable
fun QuantityControls(
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    maximum: Int,
    modifier: Modifier = Modifier,
    pulsesMax: Boolean = false,
    onMax: () -> Unit = {},
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton(HudIcons.minus, "Fewer", QuantityRange.canDecrease(quantity)) {
                onQuantityChange(QuantityRange.fewer(quantity))
            }
            BasicText(
                NumberFormat.getIntegerInstance(Locale.US).format(quantity),
                Modifier.widthIn(min = 70.dp),
                style = HudType.quantity.copy(color = Theme.textPrimary, textAlign = TextAlign.Center),
            )
            StepButton(HudIcons.plus, "More", QuantityRange.canIncrease(quantity, maximum)) {
                onQuantityChange(QuantityRange.more(quantity, maximum))
            }
            Box(
                Modifier
                    .pulsing(pulsesMax)
                    .height(40.dp)
                    .background(Theme.accent.copy(alpha = 0.15f), CircleShape)
                    .clickable(role = Role.Button) {
                        onMax()
                        onQuantityChange(QuantityRange.max(maximum))
                    }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Max", style = HudType.subheadline.copy(fontWeight = FontWeight.Bold, color = Theme.accent))
            }
        }
        if (maximum > 1) {
            Slider(
                value = quantity.toFloat(),
                onValueChange = { onQuantityChange(it.toInt().coerceIn(1, maximum)) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Quantity" },
                valueRange = 1f..maximum.toFloat(),
                steps = max(0, maximum - 2),
                colors = SliderDefaults.colors(thumbColor = Theme.accent, activeTrackColor = Theme.accent),
            )
        }
    }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, Theme.glassEdge, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        HudIcon(icon, Theme.textPrimary, size = 20.dp)
    }
}
