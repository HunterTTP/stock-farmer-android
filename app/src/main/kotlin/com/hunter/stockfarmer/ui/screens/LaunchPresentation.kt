package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.presentation.LaunchSequence

internal data class LaunchPresentation(
    val error: String?,
    val showsLoadingMessage: Boolean,
    val label: String,
) {
    companion object {
        fun transition(
            sequence: LaunchSequence,
            stage: LaunchSequence.Stage,
        ): LaunchSequence =
            if (sequence.stage == LaunchSequence.Stage.failed && stage != LaunchSequence.Stage.failed) {
                LaunchSequence(stage = stage)
            } else {
                sequence.copy(stage = stage)
            }

        /**
         * The renderer's progress as iOS reports it: the scene is built, then a first frame is drawn (textures load while it
         * draws, so there is no separate preload), then one more frame confirms the farm is on screen.
         */
        fun stage(
            isLoaded: Boolean,
            isLoading: Boolean,
            isSceneBuilt: Boolean,
            framesDrawn: Int,
        ): LaunchSequence.Stage =
            when {
                !isLoaded && isLoading -> LaunchSequence.Stage.loadingFarm
                !isLoaded -> LaunchSequence.Stage.failed
                !isSceneBuilt -> LaunchSequence.Stage.buildingScene
                framesDrawn < 1 -> LaunchSequence.Stage.loadingTextures
                framesDrawn < 2 -> LaunchSequence.Stage.waitingForFrame
                else -> LaunchSequence.Stage.ready
            }

        fun from(
            sequence: LaunchSequence,
            saveError: String? = null,
        ): LaunchPresentation =
            LaunchPresentation(
                error =
                    if (sequence.stage == LaunchSequence.Stage.failed) {
                        saveError ?: "This farm could not be opened. Its save has been kept safe."
                    } else {
                        null
                    },
                showsLoadingMessage = sequence.showsLoadingMessage,
                label = if (sequence.stage == LaunchSequence.Stage.ready) "Farm ready" else "Loading your farm",
            )
    }
}
