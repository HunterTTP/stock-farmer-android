package com.hunter.stockfarmer.ui.screens

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Keep beside the farm, outside conditional screen content, so recording continues when returning to play. */
@Composable
fun PerformanceSampling(
    monitor: PerformanceMonitor,
    spriteCount: () -> Int,
) {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val currentSprites by rememberUpdatedState(spriteCount)
    LaunchedEffect(monitor, owner, context) {
        monitor.setActive(false)
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            monitor.setActive(true)
            try {
                while (true) {
                    if (monitor.isSampling) {
                        val sprites = currentSprites()
                        monitor.sample(withContext(Dispatchers.Default) { readPerformanceResources(context, sprites) })
                    }
                    delay(1000)
                }
            } finally {
                monitor.setActive(false)
            }
        }
    }
}

private fun readPerformanceResources(
    context: Context,
    sprites: Int,
): PerformanceResources {
    val memory = runCatching { Debug.getPss() / 1024.0 }.getOrNull()
    val thermal =
        if (Build.VERSION.SDK_INT >= 29) {
            when (context.getSystemService(PowerManager::class.java)?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal"

                PowerManager.THERMAL_STATUS_LIGHT, PowerManager.THERMAL_STATUS_MODERATE -> "Warm"

                PowerManager.THERMAL_STATUS_SEVERE -> "Serious"

                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN,
                -> "Critical"

                else -> "Unknown"
            }
        } else {
            "Unknown"
        }
    return PerformanceResources(memory, sprites, thermal)
}
