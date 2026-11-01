package com.hunter.stockfarmer.market

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the device has an internet connection, which only the Stock Market needs. */
class NetworkMonitor(
    context: Context,
) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val mutableOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = mutableOnline.asStateFlow()

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()

            override fun onLost(network: Network) = refresh()

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = refresh()
        }

    init {
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        refresh()
    }

    private fun refresh() {
        val capabilities = manager?.let { it.getNetworkCapabilities(it.activeNetwork) }
        mutableOnline.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    companion object {
        @Volatile private var shared: NetworkMonitor? = null

        fun of(context: Context): NetworkMonitor =
            shared ?: synchronized(this) {
                shared ?: NetworkMonitor(context).also { shared = it }
            }
    }
}
