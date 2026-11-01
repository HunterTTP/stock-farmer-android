package com.hunter.stockfarmer.farm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.hunter.stockfarmer.game.GameViewModel

/** Hosts the libGDX farm so Compose can place it on screen like any other view. */
class FarmFragment : AndroidFragmentApplication() {
    private val model: GameViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = initializeForView(FarmGame(model.session, model.performance))
}
