package com.hunter.stockfarmer.haptics

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import com.hunter.stockfarmer.core.feedback.HapticCue

/**
 * Buzzes the device through [view]. Taps fired closer together than [MINIMUM_TAP_MILLIS] are dropped, and so are
 * alerts, each among their own kind, so a drag across many tiles doesn't buzz continuously and a tap never
 * swallows a warning.
 */
class Haptics(
    private val view: View,
    private val uptimeMillis: () -> Long = SystemClock::uptimeMillis,
) {
    private var lastTap = Long.MIN_VALUE / 2
    private var lastAlert = Long.MIN_VALUE / 2

    fun play(cue: HapticCue) {
        when (cue) {
            HapticCue.Warning -> alert(HapticFeedbackConstants.LONG_PRESS, reject())
            HapticCue.Success -> alert(HapticFeedbackConstants.LONG_PRESS, confirm())
            HapticCue.Medium -> tap(HapticFeedbackConstants.LONG_PRESS)
            HapticCue.Light -> tap(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun tap(feedback: Int) = fire(feedback, MINIMUM_TAP_MILLIS, isAlert = false)

    private fun alert(
        fallback: Int,
        feedback: Int,
    ) = fire(if (feedback >= 0) feedback else fallback, MINIMUM_ALERT_MILLIS, isAlert = true)

    private fun fire(
        feedback: Int,
        interval: Long,
        isAlert: Boolean,
    ) {
        val now = uptimeMillis()
        if (now - (if (isAlert) lastAlert else lastTap) < interval) return
        if (isAlert) lastAlert = now else lastTap = now
        view.performHapticFeedback(feedback)
    }

    private fun confirm() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else -1

    private fun reject() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else -1

    private companion object {
        const val MINIMUM_TAP_MILLIS = 70L
        const val MINIMUM_ALERT_MILLIS = 150L
    }
}
