package com.hunter.stockfarmer.ui.hud

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.PhotoSaver
import kotlin.concurrent.thread

private const val PHOTO_PIXELS = 4096
private const val SMALLER_PHOTO_PIXELS = 2048

/**
 * Takes a picture of the whole farm and saves it to the gallery, trying a smaller one if the device is short of memory.
 * [tell] is called on the main thread with the outcome.
 */
internal fun takeFarmPhoto(
    context: Context,
    session: GameSession,
    tell: (String) -> Unit,
) {
    val taking = session.photos.isTakingPhoto
    if (!taking.compareAndSet(false, true)) return

    val main = runCatching { Handler(Looper.getMainLooper()) }.getOrNull()

    fun say(message: String) {
        taking.set(false)
        if (main != null) main.post { tell(message) } else tell(message)
    }

    fun save(png: ByteArray) =
        thread {
            val saved = PhotoSaver.save(context, png)
            if (saved) session.haptic(HapticCue.Success)
            say(if (saved) "Farm photo saved" else "The farm photo could not be saved")
        }

    session.photos.request(PHOTO_PIXELS) { large ->
        if (large != null) {
            save(large)
        } else {
            session.photos.request(SMALLER_PHOTO_PIXELS) { small ->
                if (small != null) {
                    save(small)
                } else {
                    say("The farm photo could not be created. Please try again.")
                }
            }
        }
    }
}
