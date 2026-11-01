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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

    fun fewer(quantity: Long): Long = maxOf(1L, quantity - 1L)

    fun more(
        quantity: Long,
        maximum: Long,
    ): Long = if (maximum <= 0L) 1L else minOf(maximum, quantity + 1L)

    fun max(maximum: Long): Long = maxOf(1L, maximum)

    fun canDecrease(quantity: Long): Boolean = quantity > 1L

    fun canIncrease(
        quantity: Long,
        maximum: Long,
    ): Boolean = maximum > 0L && quantity < maximum
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
    QuantityControls(
        quantity = quantity.toLong(),
        onQuantityChange = { onQuantityChange(it.toInt()) },
        maximum = maximum.toLong(),
        modifier = modifier,
        pulsesMax = pulsesMax,
        editable = false,
        onMax = onMax,
    )
}

/** Minus, the quantity, plus and Max, with a slider under them, for picking how many shares from 1 up to a maximum. */
@Composable
fun QuantityControls(
    quantity: Long,
    onQuantityChange: (Long) -> Unit,
    maximum: Long,
    modifier: Modifier = Modifier,
    pulsesMax: Boolean = false,
    editable: Boolean = true,
    onMax: () -> Unit = {},
) {
    var textValue by remember(quantity) { mutableStateOf(quantity.toString()) }
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton(HudIcons.minus, "Fewer", QuantityRange.canDecrease(quantity)) {
                val next = QuantityRange.fewer(quantity)
                textValue = next.toString()
                onQuantityChange(next)
            }
            if (editable) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        textValue = digits
                        val parsed = digits.toLongOrNull() ?: 0L
                        val clamped = if (maximum > 0L) parsed.coerceIn(0L, maximum) else parsed
                        onQuantityChange(clamped)
                    },
                    modifier = Modifier.widthIn(min = 70.dp).semantics { contentDescription = "Share count" },
                    textStyle = HudType.quantity.copy(color = Theme.textPrimary, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    cursorBrush = SolidColor(Theme.accent),
                )
            } else {
                BasicText(
                    NumberFormat.getIntegerInstance(Locale.US).format(quantity),
                    Modifier.widthIn(min = 70.dp),
                    style = HudType.quantity.copy(color = Theme.textPrimary, textAlign = TextAlign.Center),
                )
            }
            StepButton(HudIcons.plus, "More", QuantityRange.canIncrease(quantity, maximum)) {
                val next = QuantityRange.more(quantity, maximum)
                textValue = next.toString()
                onQuantityChange(next)
            }
            Box(
                Modifier
                    .pulsing(pulsesMax, shape = CircleShape)
                    .height(40.dp)
                    .background(Theme.accent.copy(alpha = 0.15f), CircleShape)
                    .clickable(role = Role.Button) {
                        onMax()
                        val next = QuantityRange.max(maximum)
                        textValue = next.toString()
                        onQuantityChange(next)
                    }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Max", style = HudType.subheadline.copy(fontWeight = FontWeight.Bold, color = Theme.accent))
            }
        }
        if (maximum > 1L) {
            Slider(
                value = quantity.coerceIn(1L, maximum).toFloat(),
                onValueChange = {
                    val next = it.toLong().coerceIn(1L, maximum)
                    textValue = next.toString()
                    onQuantityChange(next)
                },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Quantity" },
                valueRange = 1f..maximum.toFloat(),
                steps = if (maximum in 2..100) maximum.toInt() - 2 else 0,
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
