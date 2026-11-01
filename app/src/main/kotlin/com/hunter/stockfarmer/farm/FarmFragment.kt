package com.hunter.stockfarmer.farm

import android.view.View
import com.badlogic.gdx.backends.android.AndroidFragmentApplication

/** Hosts the libGDX farm so Compose can place it on screen like any other view. */
class FarmFragment : AndroidFragmentApplication() {
    private val game = FarmGame()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: android.os.Bundle?,
    ): View = initializeForView(game)
}
