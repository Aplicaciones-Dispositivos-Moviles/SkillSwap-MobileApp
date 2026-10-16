package com.innovify.skillswap.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// T49: avisa a la app cuando el teléfono gana o pierde internet.
// isOnline es estado de Compose: las pantallas que lo leen se actualizan solas.
class ConnectivityObserver {

    var isOnline by mutableStateOf(true)
        private set

    private var manager: ConnectivityManager? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        // Se llama también cuando la red por defecto cambia de capacidades (p. ej. al validarse internet)
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            isOnline = hasInternet(capabilities)
        }

        override fun onLost(network: Network) {
            isOnline = false
        }
    }

    private fun hasInternet(capabilities: NetworkCapabilities?): Boolean {
        return capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // Idempotente: se puede llamar en cada onCreate de la Activity.
    fun start(context: Context) {
        if (manager != null) return
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        manager = cm
        isOnline = hasInternet(cm.getNetworkCapabilities(cm.activeNetwork))
        cm.registerDefaultNetworkCallback(callback)
    }

    fun stop() {
        try {
            manager?.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            // ya estaba desregistrado
        }
        manager = null
    }
}
