package com.hunter.stockfarmer.farm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.hunter.stockfarmer.game.GameViewModel
import com.hunter.stockfarmer.settings.PlaySettings

/** Hosts the libGDX farm so Compose can place it on screen like any other view. */
class FarmFragment : AndroidFragmentApplication() {
    private val model: GameViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        initializeForView(
            FarmGame(model.session, PlaySettings.of(requireContext()), model.performance),
            farmConfiguration(),
        )

    private fun farmConfiguration() =
        // Nothing reads a sensor, libGDX audio or the depth buffer: sound is SoundPool and MediaPlayer, the farm is flat
        // sprites. Without libGDX audio nobody sets the volume stream, so MainActivity does.
        AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useRotationVectorSensor = false
            disableAudio = true
            depth = 0
            useImmersiveMode = true
        }
}
