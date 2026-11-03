package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.core.feedback.SoundCue

/**
 * The sounds of a full panel opening and closing over the farm, as iOS `present` and `dismissSheet` make them. A panel
 * closed because something in it was chosen or done is closed quietly, as that already made its own sound.
 */
internal class PanelCues(
    private val play: (SoundCue) -> Unit,
) {
    private var wasOpen = false
    private var quiet = false

    /** The panel about to close makes no sound of its own. */
    fun closeQuietly() {
        quiet = true
    }

    fun update(isOpen: Boolean) {
        if (isOpen == wasOpen) return
        wasOpen = isOpen
        if (isOpen) {
            quiet = false
            play(SoundCue.openPanel)
        } else if (quiet) {
            quiet = false
        } else {
            play(SoundCue.closePanel)
        }
    }
}
