package com.hunter.stockfarmer.ui.screens

import android.animation.ValueAnimator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.ui.theme.LessonBubble
import com.hunter.stockfarmer.ui.theme.Pulse
import com.hunter.stockfarmer.ui.theme.pulseGlow
import kotlin.math.cos

/** Keep the graphics layer in place as teaching changes so an in-progress tool drag keeps its pointer handler. */
@Composable
fun Modifier.lessonPulse(
    enabled: Boolean,
    shape: Shape = RectangleShape,
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
        scaleX = Pulse.scale(swell.value, reduceMotion)
        scaleY = scaleX
    }.pulseGlow(shape) { swell.value }
}
