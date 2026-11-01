package com.hunter.stockfarmer.ui.timelapse

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.persistence.TimelapseArchive
import com.hunter.stockfarmer.core.presentation.TimelapsePlayback
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudIcons
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class DisplayedFrame(
    val index: Int,
    val image: ImageBitmap,
)

/** Full-screen history player. Archive is an immutable snapshot for the duration of this presentation. */
@Composable
fun TimelapseViewer(
    recorder: TimelapseRecorder,
    archive: TimelapseArchive,
    farm: UUID,
    crossfades: Boolean,
    onClose: () -> Unit,
) {
    val player = remember(recorder, farm, archive.generation) { TimelapsePlayer(archive.frames.size) }
    var playback by remember(player) { mutableStateOf(player.playback) }
    var displayed by remember(player) { mutableStateOf<DisplayedFrame?>(null) }
    var error by remember(player) { mutableStateOf<String?>(null) }
    var closing by remember(player) { mutableStateOf(false) }
    val currentImage by rememberUpdatedState(displayed)
    val currentError by rememberUpdatedState(error)
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val reduceMotion =
        remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    val reveal = remember(player) { Animatable(0f) }
    val finish by rememberUpdatedState(onClose)
    val images =
        remember(player) {
            TimelapseImages(archive.frames) { frame ->
                withContext(Dispatchers.IO) {
                    val bytes = recorder.store.image(frame, farm) ?: throw IOException("Missing screenshot")
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    bitmap?.asImageBitmap() ?: throw IOException("Invalid screenshot")
                }
            }
        }

    fun pause() {
        player.pause()
        playback = player.playback
    }

    fun close() {
        if (!closing) {
            pause()
            closing = true
        }
    }

    DisposableEffect(recorder, player, owner) {
        recorder.setViewing(true)
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) pause()
            }
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) pause()
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            recorder.setViewing(false)
        }
    }
    LaunchedEffect(player, closing, reduceMotion) {
        reveal.animateTo(
            if (closing) 0f else 1f,
            tween(if (reduceMotion) 150 else 550, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)),
        )
        if (closing) finish()
    }
    LaunchedEffect(images, playback.index) {
        val index = playback.index
        if (index !in archive.frames.indices) return@LaunchedEffect
        try {
            displayed = DisplayedFrame(index, images.current(index))
            error = null
            images.prefetch(index)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            displayed = null
            error = "This screenshot could not be opened. Try another point on the timeline."
            pause()
        }
    }
    LaunchedEffect(player, owner, playback.isPlaying, closing) {
        if (!playback.isPlaying || closing) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last: Long? = null
            while (isActive && player.playback.isPlaying) {
                withFrameNanos { now ->
                    val seconds = last?.let { (now - it) / 1_000_000_000.0 } ?: 0.0
                    last = now
                    player.advance(seconds, currentImage?.index == player.playback.index && currentError == null)
                    playback = player.playback
                }
            }
        }
    }
    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        HudMaterialTheme {
            Box(
                Modifier.fillMaxSize().timelapseReveal(reveal.value, reduceMotion).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val duration = player.transitionMillis(crossfades, reduceMotion)
                Crossfade(
                    displayed,
                    animationSpec = tween(duration, easing = LinearEasing),
                    label = "Timelapse frame",
                ) { picture ->
                    if (picture != null) {
                        Image(
                            picture.image,
                            "Farm screenshot, day ${archive.frames[picture.index].day}",
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
                if (displayed == null) {
                    if (error != null) {
                        Text(error.orEmpty(), Modifier.padding(32.dp), color = Color.White)
                    } else if (archive.frames.isEmpty()) {
                        Text("No screenshots", color = Color.White)
                    } else {
                        CircularProgressIndicator()
                    }
                }
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .clickable(enabled = !closing, role = Role.Button, onClick = ::close),
                            contentAlignment = Alignment.Center,
                        ) {
                            HudIcon(HudIcons.xmark, Color.White, size = 16.dp, contentDescription = "Close timelapse")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    PlaybackControls(
                        playback,
                        archive,
                        enabled = !closing,
                        onToggle = {
                            player.togglePlaying()
                            playback = player.playback
                        },
                        onSeek = {
                            player.beginScrubbing()
                            player.seek(it)
                            playback = player.playback
                        },
                        onScrubEnd = {
                            player.endScrubbing()
                            playback = player.playback
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    playback: TimelapsePlayback,
    archive: TimelapseArchive,
    enabled: Boolean,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onScrubEnd: () -> Unit,
) {
    val count = playback.count
    Column(
        Modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val canToggle = enabled && count > 1
            Box(
                Modifier
                    .size(44.dp)
                    .alpha(if (canToggle) 1f else 0.4f)
                    .clickable(enabled = canToggle, role = Role.Button, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                HudIcon(
                    if (playback.isPlaying) HudIcons.pauseFill else HudIcons.playFill,
                    Color.White,
                    size = 20.dp,
                    contentDescription = if (playback.isPlaying) "Pause timelapse" else "Play timelapse",
                )
            }
            archive.frames.getOrNull(playback.index)?.let { frame ->
                Text(
                    "Day ${frame.day} · ${DayNightCycle.clockText(frame.minute.toDouble())}",
                    color = Color.White,
                    style = HudType.subheadline,
                )
            }
            Spacer(Modifier.weight(1f))
            Text("${if (count == 0) 0 else playback.index + 1} / $count", color = Color.White, style = HudType.caption)
        }
        Slider(
            value = playback.index.toFloat(),
            onValueChange = { onSeek(it.roundToInt()) },
            onValueChangeFinished = onScrubEnd,
            valueRange = 0f..maxOf(1, count - 1).toFloat(),
            steps = maxOf(0, count - 2),
            enabled = enabled && count > 1,
            modifier =
                Modifier.semantics {
                    contentDescription = "Timelapse timeline, screenshot ${playback.index + 1} of $count"
                },
            colors = SliderDefaults.colors(thumbColor = Theme.accent, activeTrackColor = Theme.accent),
        )
    }
}

private fun Modifier.timelapseReveal(
    progress: Float,
    reduceMotion: Boolean,
): Modifier =
    if (reduceMotion) {
        graphicsLayer { alpha = progress }
    } else {
        drawWithContent {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = hypot(center.x, center.y) * progress
            val circle = Path().apply { addOval(Rect(center, radius)) }
            clipPath(circle) { this@drawWithContent.drawContent() }
        }
    }
