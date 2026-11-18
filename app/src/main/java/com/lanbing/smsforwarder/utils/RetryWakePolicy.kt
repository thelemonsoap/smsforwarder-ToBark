package com.lanbing.smsforwarder.utils

/** No heartbeat: wait for the earliest queued SMS, or the connectivity callback when offline. */
object RetryWakePolicy {
    fun delay(pendingDelays: List<Long>, online: Boolean): Long? =
        if (!online || pendingDelays.isEmpty()) null else pendingDelays.minOrNull()!!.coerceAtLeast(10_000L)
}
