package com.hunter.stockfarmer.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlin.math.roundToInt

/** Where the Graphics and Gameplay settings are kept between runs. */
interface PlaySettingsStorage {
    fun boolean(
        key: String,
        default: Boolean,
    ): Boolean

    fun float(
        key: String,
        default: Float,
    ): Float

    fun putBoolean(
        key: String,
        value: Boolean,
    )

    fun putFloat(
        key: String,
        value: Float,
    )
}

private class PreferencePlayStorage(
    private val preferences: SharedPreferences,
) : PlaySettingsStorage {
    override fun boolean(
        key: String,
        default: Boolean,
    ) = preferences.getBoolean(key, default)

    override fun float(
        key: String,
        default: Float,
    ) = preferences.getFloat(key, default)

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) = preferences.edit { putBoolean(key, value) }

    override fun putFloat(
        key: String,
        value: Float,
    ) = preferences.edit { putFloat(key, value) }
}

/**
 * The settings behind the Graphics and Gameplay pages (flags, shadow intensity and tool reach). Reads
 * are Compose state, so the HUD and the pages follow a change at once.
 */
class PlaySettings(
    private val storage: PlaySettingsStorage,
) {
    private var dynamicShadowsState by mutableStateOf(storage.boolean(DYNAMIC_SHADOWS, DEFAULT_DYNAMIC_SHADOWS))
    private var intensity by mutableStateOf(clampedIntensity(storage.float(SHADOW_INTENSITY, DEFAULT_SHADOW_INTENSITY)))
    private var gridShownState by mutableStateOf(storage.boolean(GRID_LINES, DEFAULT_SHOWN))
    private var moneyShownState by mutableStateOf(storage.boolean(MONEY_PILL, DEFAULT_SHOWN))
    private var levelShownState by mutableStateOf(storage.boolean(LEVEL_PILL, DEFAULT_SHOWN))
    private var reach by mutableStateOf(clampedReach(storage.float(TOOL_REACH, DEFAULT_TOOL_REACH)))

    val isDynamicShadowsEnabled: Boolean get() = dynamicShadowsState
    val shadowIntensity: Float get() = intensity
    val isGridShown: Boolean get() = gridShownState
    val isMoneyShown: Boolean get() = moneyShownState
    val isLevelShown: Boolean get() = levelShownState
    val toolReach: Float get() = reach

    fun setDynamicShadows(enabled: Boolean) {
        dynamicShadowsState = enabled
        storage.putBoolean(DYNAMIC_SHADOWS, enabled)
    }

    fun setShadowIntensity(value: Float) {
        intensity = clampedIntensity(value)
        storage.putFloat(SHADOW_INTENSITY, intensity)
    }

    fun setGridShown(shown: Boolean) {
        gridShownState = shown
        storage.putBoolean(GRID_LINES, shown)
    }

    fun setMoneyShown(shown: Boolean) {
        moneyShownState = shown
        storage.putBoolean(MONEY_PILL, shown)
    }

    fun setLevelShown(shown: Boolean) {
        levelShownState = shown
        storage.putBoolean(LEVEL_PILL, shown)
    }

    fun setToolReach(points: Float) {
        reach = clampedReach(points)
        storage.putFloat(TOOL_REACH, reach)
    }

    /** Every graphics setting back to how the game first came. */
    fun resetGraphicsDefaults() {
        setDynamicShadows(DEFAULT_DYNAMIC_SHADOWS)
        setShadowIntensity(DEFAULT_SHADOW_INTENSITY)
        setGridShown(DEFAULT_SHOWN)
        setMoneyShown(DEFAULT_SHOWN)
        setLevelShown(DEFAULT_SHOWN)
    }

    fun resetGameplayDefaults() = setToolReach(DEFAULT_TOOL_REACH)

    companion object {
        const val DEFAULT_TOOL_REACH = 16f
        const val MAX_TOOL_REACH = 200f
        const val DEFAULT_SHADOW_INTENSITY = 0.5f
        private const val DEFAULT_DYNAMIC_SHADOWS = false
        private const val DEFAULT_SHOWN = true

        private const val MONEY_PILL = "moneyPillShown"
        private const val LEVEL_PILL = "levelPillShown"
        private const val GRID_LINES = "gridLinesShown"
        private const val DYNAMIC_SHADOWS = "dynamicShadows"
        private const val SHADOW_INTENSITY = "shadowIntensity"
        private const val TOOL_REACH = "toolReach"

        fun clampedIntensity(intensity: Float): Float = intensity.coerceIn(0f, 1f)

        fun clampedReach(points: Float): Float = points.roundToInt().toFloat().coerceIn(0f, MAX_TOOL_REACH)

        private var shared: PlaySettings? = null

        /** The one set of settings the HUD and the settings pages share. */
        @Synchronized
        fun of(context: Context): PlaySettings =
            shared ?: PlaySettings(
                PreferencePlayStorage(context.applicationContext.getSharedPreferences("play", Context.MODE_PRIVATE)),
            ).also { shared = it }
    }
}
