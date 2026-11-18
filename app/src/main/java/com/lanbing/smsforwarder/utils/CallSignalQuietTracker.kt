package com.lanbing.smsforwarder.utils

/** Monotonic clock; duplicate IDLE never extends the cooldown. No phone numbers are kept. */
class CallSignalQuietTracker(private val graceMillis: Long = 30_000L) {
    private val callbacks = mutableSetOf<Int>()
    private val broadcasts = mutableSetOf<Int>()
    val busy: Boolean get() = callbacks.isNotEmpty() || broadcasts.isNotEmpty()
    var quietUntil: Long = 0L
        private set

    fun muted(now: Long): Boolean = busy || now < quietUntil

    fun restore(remaining: Long, now: Long) {
        quietUntil = now + remaining.coerceIn(0L, graceMillis)
    }

    fun observe(id: Int, state: CallState, fromBroadcast: Boolean, now: Long) {
        val wasBusy = busy
        if (fromBroadcast) {
            if (state == CallState.IDLE) {
                // An unscoped broadcast is aggregate: callbacks remain authoritative per SIM.
                if (id < 0) broadcasts.clear() else broadcasts.remove(id)
            } else if (id !in callbacks && !(id < 0 && busy)) {
                if (id >= 0) broadcasts.remove(-1)
                broadcasts.add(id)
            }
        } else {
            broadcasts.remove(id)
            if (state == CallState.IDLE) callbacks.remove(id)
            else {
                // Adopt an earlier unscoped ringing broadcast, rather than keep two calls.
                broadcasts.remove(-1)
                callbacks.add(id)
            }
        }
        if (busy) quietUntil = 0L
        else if (wasBusy) quietUntil = now + graceMillis
    }

    fun retainSubscriptions(ids: Set<Int>, now: Long) {
        val wasBusy = busy
        callbacks.retainAll(ids)
        broadcasts.removeAll { it >= 0 && it !in ids }
        if (wasBusy && !busy) quietUntil = now + graceMillis
    }
}
