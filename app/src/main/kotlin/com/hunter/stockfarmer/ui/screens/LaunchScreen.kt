package com.hunter.stockfarmer.ui.screens

import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.presentation.LaunchSequence
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.Theme
import kotlin.math.hypot

/** Advance stages from actual loader/renderer readiness. Keep the farm underneath until onComplete. */
@Composable
fun LaunchScreen(
    stage: LaunchSequence.Stage = LaunchSequence.Stage.loadingFarm,
    saveError: String? = null,
    onRetry: () -> Unit = {},
    onChooseFarm: () -> Unit = {},
    onComplete: () -> Unit = {},
    reduceMotion: Boolean = !ValueAnimator.areAnimatorsEnabled(),
) {
    var sequence by remember { mutableStateOf(LaunchSequence(stage = stage)) }
    val owner = LocalLifecycleOwner.current
    val complete by rememberUpdatedState(onComplete)
    LaunchedEffect(stage, owner, reduceMotion) {
        sequence = LaunchPresentation.transition(sequence, stage)
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous: Long? = null
            while (!sequence.isComplete && stage != LaunchSequence.Stage.failed) {
                withFrameNanos { now ->
                    previous?.let {
                        sequence = sequence.advance(((now - it) / 1e9).coerceAtMost(0.05), true, reduceMotion)
                    }
                    previous = now
                }
            }
        }
    }
    LaunchedEffect(sequence.isComplete) { if (sequence.isComplete) complete() }
    LaunchScreen(sequence, saveError, onRetry, onChooseFarm, reduceMotion)
}

/** Stateless variant for hosts that already own a LaunchSequence. */
@Composable
fun LaunchScreen(
    sequence: LaunchSequence,
    saveError: String? = null,
    onRetry: () -> Unit = {},
    onChooseFarm: () -> Unit = {},
    reduceMotion: Boolean = !ValueAnimator.areAnimatorsEnabled(),
) {
    if (sequence.isComplete) return
    val presentation = LaunchPresentation.from(sequence, saveError)
    HudMaterialTheme {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (reduceMotion) 1f - sequence.revealProgress.toFloat() else 1f
                }.drawWithContent {
                    val reveal = sequence.revealProgress.toFloat()
                    if (reduceMotion || reveal == 0f) {
                        drawContent()
                    } else {
                        val radius = hypot(size.width / 2, size.height / 2) * reveal
                        val hole =
                            Path().apply {
                                addOval(
                                    Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius),
                                )
                            }
                        clipPath(hole, ClipOp.Difference) { this@drawWithContent.drawContent() }
                    }
                }.background(Theme.launchBackground)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
        ) {
            val iconWidth = minOf(maxWidth * 0.58f, 260.dp)
            Column(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = -maxHeight * 0.04f)
                    .graphicsLayer { alpha = sequence.contentOpacity.toFloat() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                ScreenIcon(ScreenArt("images/AppIcon.png"), symbol = "", size = iconWidth)
                if (presentation.error != null) {
                    Column(
                        Modifier.widthIn(max = 388.dp).padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            presentation.error,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = onRetry) { Text("Retry") }
                            Button(onClick = onChooseFarm) { Text("Choose Farm") }
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .width(iconWidth)
                            .height(6.dp)
                            .background(Color.White.copy(alpha = 0.09f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .semantics {
                                contentDescription = presentation.label
                                progressBarRangeInfo =
                                    ProgressBarRangeInfo(sequence.displayedProgress.toFloat(), 0f..1f)
                            },
                    ) {
                        Box(
                            Modifier
                                .width(maxOf(6.dp, iconWidth * sequence.displayedProgress.toFloat()))
                                .height(6.dp)
                                .background(Theme.accent, CircleShape),
                        )
                    }
                    Text(
                        if (presentation.showsLoadingMessage) "Preparing your farm…" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
