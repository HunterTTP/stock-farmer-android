package com.hunter.stockfarmer.core.presentation

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Startup timings count only foreground time. Readiness always comes from the loader and renderer. */
data class LaunchSequence(
    val stage: Stage = Stage.loadingFarm,
    val displayedProgress: Double = 0.0,
    private val visibleElapsed: Double = 0.0,
    private val readyElapsed: Double = 0.0,
    private val revealDuration: Double = 0.55,
) {
    @Suppress("ktlint:standard:enum-entry-name-case")
    enum class Stage { loadingFarm, buildingScene, loadingTextures, waitingForFrame, ready, failed }

    val progress: Double get() =
        when (stage) {
            Stage.loadingFarm -> 0.08
            Stage.buildingScene -> 0.45
            Stage.loadingTextures -> 0.7
            Stage.waitingForFrame -> 0.92
            Stage.ready -> 1.0
            Stage.failed -> 0.0
        }
    val showsLoadingMessage: Boolean get() = visibleElapsed >= 1.5 && stage != Stage.failed && stage != Stage.ready
    val contentOpacity: Double get() = max(0.0, 1 - readyElapsed / fadeDuration)
    val revealProgress: Double get() {
        val fraction = min(1.0, max(0.0, (readyElapsed - fadeDuration) / revealDuration))
        return fraction * fraction * (3 - 2 * fraction)
    }
    val isComplete: Boolean get() = readyElapsed + 0.000001 >= fadeDuration + revealDuration

    fun advance(
        by: Double,
        isActive: Boolean,
        reduceMotion: Boolean,
    ): LaunchSequence {
        if (!isActive || !by.isFinite() || by <= 0 || isComplete) return this
        val introductionLeft = max(0.0, introductionDuration - visibleElapsed)
        val rate = if (stage == Stage.ready) readyGlideRate else glideRate
        val displayed =
            if (progress > displayedProgress) {
                if (reduceMotion) {
                    progress
                } else {
                    displayedProgress + (progress - displayedProgress) * (1 - exp(-rate * by))
                }
            } else {
                displayedProgress
            }
        val readyGain = if (stage == Stage.ready) max(0.0, by - introductionLeft) else 0.0
        return copy(
            visibleElapsed = visibleElapsed + by,
            revealDuration = if (reduceMotion) 0.15 else 0.55,
            displayedProgress = displayed,
            readyElapsed = readyElapsed + readyGain,
        )
    }

    companion object {
        private val introductionDuration = 0.35
        private val fadeDuration = 0.12
        private val glideRate = 3.0
        private val readyGlideRate = 20.0
    }
}
