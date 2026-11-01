package com.hunter.stockfarmer.ui.screens

import android.animation.ValueAnimator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos

/** Place above the tool circle; the owner uses state.pulses and the targets to highlight its existing controls. */
@Composable
fun LessonGuide(
    state: LessonGuideState,
    modifier: Modifier = Modifier,
) {
    val message = state.message ?: return
    ScreenTheme {
        Text(
            message,
            modifier
                .widthIn(max = 280.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Keep the graphics layer in place as teaching changes so an in-progress tool drag keeps its pointer handler. */
@Composable
fun Modifier.lessonPulse(
    enabled: Boolean,
    reduceMotion: Boolean = !ValueAnimator.areAnimatorsEnabled(),
): Modifier {
    val swell: State<Float> =
        if (enabled && !reduceMotion) {
            rememberInfiniteTransition(label = "Lesson pulse").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(800, easing = { ((1 - cos(it * Math.PI)) / 2).toFloat() }),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "Lesson swell",
            )
        } else {
            rememberUpdatedState(if (enabled) 0.5f else 0f)
        }
    return graphicsLayer {
        val amount = swell.value
        scaleX = if (reduceMotion) 1f else 1 + 0.05f * amount
        scaleY = scaleX
        shadowElevation = 7.dp.toPx() * amount
        ambientShadowColor = FarmGold.copy(alpha = 0.8f * amount)
        spotShadowColor = ambientShadowColor
    }
}
