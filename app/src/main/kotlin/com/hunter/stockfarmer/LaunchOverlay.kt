package com.hunter.stockfarmer

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.core.presentation.LaunchSequence
import com.hunter.stockfarmer.game.GameViewModel
import com.hunter.stockfarmer.ui.rememberPolledState
import com.hunter.stockfarmer.ui.screens.LaunchPresentation
import com.hunter.stockfarmer.ui.screens.LaunchScreen
import com.hunter.stockfarmer.ui.screens.SaveSlotsScreen
import com.hunter.stockfarmer.ui.theme.Theme

private const val RENDERER_POLL_MILLIS = 16L
private const val FAILED_POLL_MILLIS = 500L

/**
 * The launch screen over the farm: shown while the save loads, the scene builds and the audio gets ready, and again after
 * a failed load.
 */
@Composable
fun BoxScope.LaunchOverlay(
    model: GameViewModel,
    sound: SoundCenter,
) {
    val persistence = model.persistenceState
    var launchId by remember { mutableIntStateOf(0) }
    var launching by remember { mutableStateOf(true) }
    LaunchedEffect(persistence.isReady) {
        if (!persistence.isReady && !launching) {
            launchId++
            launching = true
        }
    }
    if (launching || !persistence.isReady) {
        key(launchId) {
            var choosingFarm by remember { mutableStateOf(false) }
            LaunchedEffect(persistence.isReady) {
                if (persistence.isReady) sound.prepareAudio(model.session.read { it.season })
            }
            // A failed load has no scene to wait for, so it only looks now and then.
            val failedToLoad = !persistence.isReady && !persistence.isLoading
            val rendered by rememberPolledState(
                Unit,
                if (failedToLoad) FAILED_POLL_MILLIS else RENDERER_POLL_MILLIS,
            ) {
                val view = model.session.view
                Triple(view.isSceneBuilt, view.framesDrawn.coerceAtMost(2), sound.isAudioReady)
            }
            val (isSceneBuilt, framesDrawn, isAudioReady) = rendered
            val stage =
                LaunchPresentation.stage(
                    persistence.isReady,
                    persistence.isLoading,
                    isSceneBuilt,
                    framesDrawn,
                    isAudioReady,
                )
            LaunchScreen(
                stage = stage,
                saveError = persistence.error,
                onRetry = model::retry,
                onChooseFarm = {
                    if (stage == LaunchSequence.Stage.failed) {
                        choosingFarm = true
                    }
                },
                onComplete = { launching = false },
            )
            if (stage == LaunchSequence.Stage.failed) {
                Column(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
                    persistence.backups.forEach { backup ->
                        TextButton(onClick = { model.restore(backup) }) {
                            Text(stringResource(R.string.restore_backup, backup.kind.name), color = Theme.accent)
                        }
                    }
                }
                if (choosingFarm) {
                    SaveSlotsScreen(
                        model.farms,
                        onClose = { choosingFarm = false },
                        saveError = persistence.error,
                        onRetrySave = model::retry,
                    )
                }
            }
        }
    }
}
