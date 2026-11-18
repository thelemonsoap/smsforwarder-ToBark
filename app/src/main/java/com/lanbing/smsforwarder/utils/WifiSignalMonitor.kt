package com.lanbing.smsforwarder.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

/** Passive connectivity callbacks only: no Wi-Fi scan, polling, SSID access or network request. */
class WifiSignalMonitor(context: Context, private val changed: () -> Unit) {
    private val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val state = WifiPresenceTracker<Network>()
    private var token: Any? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile var connected = false
        private set
    @Volatile var epoch = 0L
        private set

    fun start(): Boolean = synchronized(lock) {
        if (callback != null) return true
        val cm = manager ?: return false
        val identity = Any()
        token = identity
        val listener = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update(identity) { state.available(network) }
            override fun onLost(network: Network) = update(identity) { state.lost(network) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                update(identity) { state.capabilities(network, caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) }
        }
        try {
            val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
            // Hold the lock until the startup snapshot is seeded, so concurrent callbacks apply after it.
            cm.registerNetworkCallback(request, listener)
            callback = listener
            state.replace(snapshot())
            connected = state.connected; epoch = state.epoch
            true
        } catch (_: Exception) {
            try { cm.unregisterNetworkCallback(listener) } catch (_: Exception) { }
            token = null; callback = null
            false
        }
    }

    private fun update(identity: Any, action: () -> Boolean) {
        synchronized(lock) {
            if (token !== identity) return
            val transition = action()
            connected = state.connected; epoch = state.epoch
            if (transition) handler.post {
                val current = synchronized(lock) { token === identity }
                if (current) changed()
            }
        }
    }

    private fun snapshot(): Set<Network> = manager?.allNetworks.orEmpty().filter {
        manager?.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.toSet()

    /** Last-moment guard for a queued HTTP send, even if its network callback has not arrived yet. */
    fun connectedForDelivery(): Boolean = connected || try { snapshot().isNotEmpty() } catch (_: Exception) { true }

    fun stop() {
        val listener = synchronized(lock) {
            token = null
            callback.also {
                callback = null
                state.replace(emptySet()); connected = false; epoch = state.epoch
            }
        }
        listener?.let { try { manager?.unregisterNetworkCallback(it) } catch (_: Exception) { } }
    }
}
