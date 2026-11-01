package com.hunter.stockfarmer

import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.hunter.stockfarmer.farm.FarmFragment

class MainActivity :
    FragmentActivity(),
    AndroidFragmentApplication.Callbacks {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    FarmHost()
                    Text("Stock Farmer", Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }

    @Composable
    private fun FarmHost() {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FragmentContainerView(context).apply {
                    id = View.generateViewId()
                    if (supportFragmentManager.findFragmentByTag(FARM_TAG) == null) {
                        supportFragmentManager.commit { replace(id, FarmFragment(), FARM_TAG) }
                    }
                }
            },
        )
    }

    override fun exit() = Unit

    private companion object {
        const val FARM_TAG = "farm"
    }
}
