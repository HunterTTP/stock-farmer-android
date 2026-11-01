package com.hunter.stockfarmer.audio

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hunter.stockfarmer.core.feedback.SoundCue
import java.util.concurrent.Executors

/** Decodes once off the UI thread. A cue is published only when all its variants have finished loading. */
internal class SoundPoolOutput(
    private val assets: AssetManager,
) : EffectOutput {
    private data class Asset(
        val path: String,
        val duration: Double,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private val pool =
        SoundPool
            .Builder()
            .setMaxStreams(SoundEffectsPlayer.VOICE_COUNT)
            .setAudioAttributes(gameAudioAttributes())
            .build()
    private val pending = mutableMapOf<Int, Pair<SoundCue, EffectClip>>()
    private val loading = mutableMapOf<SoundCue, MutableList<EffectClip>>()
    private val ready = mutableMapOf<SoundCue, List<EffectClip>>()
    private var closed = false

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (!closed) {
                val (cue, clip) = pending.remove(id) ?: return@setOnLoadCompleteListener
                if (status == 0) {
                    loading.getOrPut(cue) { mutableListOf() }.add(clip)
                } else {
                    Log.w(TAG, "Could not decode ${cue.name}: $status")
                }
                if (pending.values.none { it.first == cue }) {
                    ready[cue] = loading.remove(cue).orEmpty().sortedBy { it.id }
                }
            }
        }
        loader.execute {
            val catalog =
                SoundCue.entries.associateWith { cue ->
                    val folder = "audio/Effects/${cue.clips.folder}"
                    val names = runCatching { assets.list(folder).orEmpty().toList() }.getOrDefault(emptyList())
                    names
                        .filter { it.endsWith(".ogg") && cue.clips.includes(it.removeSuffix(".ogg")) }
                        .sorted()
                        .mapNotNull { name ->
                            val path = "$folder/$name"
                            runCatching {
                                val clip = assets.open(path).use(OggClip::read)
                                require(clip.byteCount <= 1_048_576) { "Clip exceeds SoundPool's decoded buffer" }
                                Asset(path, clip.duration)
                            }.onFailure { Log.w(TAG, "Could not read $path", it) }.getOrNull()
                        }
                }
            handler.post { if (!closed) load(catalog) }
            loader.shutdown()
        }
    }

    private fun load(catalog: Map<SoundCue, List<Asset>>) {
        for ((cue, clips) in catalog) {
            for (clip in clips) {
                runCatching {
                    val id = assets.openFd(clip.path).use { pool.load(it, 1) }
                    if (id != 0) {
                        pending[id] = cue to EffectClip(id, clip.duration)
                    } else {
                        Log.w(TAG, "Could not load ${clip.path}")
                    }
                }.onFailure { Log.w(TAG, "Could not load ${clip.path}", it) }
            }
        }
    }

    override fun clips(cue: SoundCue): List<EffectClip> = ready[cue].orEmpty()

    override fun play(
        clip: EffectClip,
        volume: StereoVolume,
    ): Int = if (closed) 0 else pool.play(clip.id, volume.left, volume.right, 1, 0, 1f)

    override fun stop(stream: Int) {
        if (!closed) pool.stop(stream)
    }

    override fun close() {
        if (closed) return
        closed = true
        loader.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        pool.setOnLoadCompleteListener(null)
        pool.release()
        pending.clear()
        loading.clear()
        ready.clear()
    }

    companion object {
        private const val TAG = "StockFarmerAudio"
    }
}

internal fun gameAudioAttributes(): AudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
