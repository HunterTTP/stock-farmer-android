package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.rules.NightPulse

/**
 * The chime that rings as the midnight wave sets off. [ring] says whether the sound started; a chime that was dropped,
 * with every voice busy or the sounds not yet running, is tried again while the music is still hushed for it.
 */
class NightChime(
    private val ring: () -> Boolean,
) {
    private var night: Int? = null
    private var isPending = false

    fun begin(night: Int?) {
        this.night = night
        isPending = !ring()
    }

    /** Whether this session rang the chime of [night], which hushes the music for it: a clock set by hand into its minutes rings nothing. */
    fun hushes(night: Int): Boolean = this.night == night

    fun retry(minuteOfDay: Double) {
        if (isPending) isPending = NightPulse.isMusicHushed(minuteOfDay) && !ring()
    }
}
