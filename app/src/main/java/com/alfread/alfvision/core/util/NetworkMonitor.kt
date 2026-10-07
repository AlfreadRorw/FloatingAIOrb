package com.alfread.alfvision.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkMonitor(context: Context) {
    private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)
    private val _connected = MutableStateFlow(isConnected())
    val connected: StateFlow<Boolean> = _connected

    init {
        // Status jaringan sekarang live, bukan sekadar snapshot saat app dibuka.
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
            })
        }
    }

    fun refresh() { _connected.value = isConnected() }

    fun isConnected(): Boolean = runCatching {
        val manager = cm ?: return@runCatching false
        val network = manager.activeNetwork ?: return@runCatching false
        val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
}
