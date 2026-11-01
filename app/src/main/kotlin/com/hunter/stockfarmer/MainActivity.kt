package com.hunter.stockfarmer

import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.hunter.stockfarmer.farm.FarmFragment
import com.hunter.stockfarmer.game.GameViewModel
import com.hunter.stockfarmer.ui.Hud

class MainActivity :
    FragmentActivity(),
    AndroidFragmentApplication.Callbacks {
    private val model: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Compose creates the host after loading; a restored fragment still refers to the old host's view ID.
        supportFragmentManager.findFragmentByTag(FARM_TAG)?.let { farm ->
            supportFragmentManager.commitNow { remove(farm) }
        }
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    val persistence = model.persistenceState
                    if (persistence.isReady) {
                        FarmHost()
                        Hud(model.session, Modifier.fillMaxSize())
                        persistence.error?.let { message ->
                            AlertDialog(
                                onDismissRequest = {},
                                title = { Text("Save failed") },
                                text = { Text(message) },
                                confirmButton = { TextButton(onClick = model::retry) { Text("Retry") } },
                            )
                        }
                    } else {
                        Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                            if (persistence.isLoading) {
                                CircularProgressIndicator()
                                Text("Opening your farm…")
                            } else {
                                Text(persistence.error.orEmpty())
                                Button(onClick = model::retry) { Text("Retry") }
                                persistence.backups.forEach { backup ->
                                    TextButton(onClick = { model.restore(backup) }) {
                                        Text("Restore ${backup.kind.name} backup")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.resume()
    }

    override fun onPause() {
        model.pause()
        super.onPause()
    }

    override fun onStop() {
        model.pause()
        super.onStop()
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
