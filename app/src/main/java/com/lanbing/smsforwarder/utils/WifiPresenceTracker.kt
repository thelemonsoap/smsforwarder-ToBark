package com.lanbing.smsforwarder.utils

/** Track all matching Wi-Fi networks, not only the default route (which may be a VPN). */
class WifiPresenceTracker<K> {
    private val networks = mutableSetOf<K>()
    var epoch: Long = 0L
        private set
    val connected: Boolean get() = networks.isNotEmpty()

    fun available(network: K): Boolean = change { networks.add(network) }
    fun lost(network: K): Boolean = change { networks.remove(network) }
    fun capabilities(network: K, wifi: Boolean): Boolean = if (wifi) available(network) else lost(network)
    fun replace(current: Set<K>): Boolean = change { networks.clear(); networks.addAll(current) }

    private inline fun change(action: () -> Unit): Boolean {
        val before = connected
        action()
        return (before != connected).also { if (it) epoch++ }
    }
}

object SignalWifiRules {
    fun tracker(saved: MobileSignal?, announceInitial: Boolean, needsWifiBaseline: Boolean) =
        if (needsWifiBaseline) SignalTransitionTracker(null, false)
        else SignalTransitionTracker(saved, announceInitial)

    fun mayDeliver(connected: Boolean, currentEpoch: Long, queuedEpoch: Long): Boolean =
        !connected && currentEpoch == queuedEpoch
}
