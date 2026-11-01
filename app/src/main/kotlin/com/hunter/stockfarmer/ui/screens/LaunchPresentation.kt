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
