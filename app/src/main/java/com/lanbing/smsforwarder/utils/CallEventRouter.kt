package com.lanbing.smsforwarder.utils

data class RoutedCallAlert(val token: Long, val alert: CallAlert, val sim: String)
data class CallEventResult(val changed: Boolean, val sim: String, val alert: RoutedCallAlert?)

/** Both Android callbacks and PHONE_STATE broadcasts feed one session per subscription. */
class CallEventRouter {
    private class Session {
        val tracker = CallSessionTracker()
        var state: CallState? = null
        var current: RoutedCallAlert? = null
        var sim = ""
        var ended: RoutedCallAlert? = null
        var endedAt = 0L
        var offhookAt = 0L
    }
    private val sessions = mutableMapOf<Int, Session>()
    private val snapshots = linkedMapOf<Long, RoutedCallAlert>()
    private var knownSubscriptions = emptySet<Int>()
    private var nextToken = 0L

    fun setSubscriptions(ids: Set<Int>) { knownSubscriptions = ids.filter { it >= 0 }.toSet() }

    fun onEvent(subscriptionId: Int?, sim: String, state: CallState, number: String?, now: Long, fromBroadcast: Boolean = false): CallEventResult? {
        val id = subscriptionId?.takeIf { it >= 0 }
        val trimmedNumber = number?.trim().orEmpty()
        val key = if (id != null) {
            // Some OEM broadcasts omit subscription data. Adopt that ringing session once
            // the per-SIM callback identifies it, preserving its one incoming alert.
            if (state == CallState.RINGING && sessions[id]?.current == null &&
                sessions[-1]?.current != null && sessions[-1]?.state == CallState.RINGING) {
                sessions[id] = sessions.remove(-1)!!
            }
            id
        } else {
            val active = sessions.filterValues { it.current != null }
            val matches = active.filterValues {
                state != CallState.RINGING || it.state == CallState.RINGING ||
                    (fromBroadcast && it.state == CallState.OFFHOOK && now - it.offhookAt in 0L..5_000L &&
                        (trimmedNumber.isBlank() || it.current!!.alert.number.isBlank() || it.current!!.alert.number == trimmedNumber))
            }
            val numbered = matches.filterValues {
                trimmedNumber.isNotEmpty() && it.current?.alert?.number == trimmedNumber
            }
            when {
                numbered.size == 1 -> numbered.keys.single()
                matches.size == 1 -> matches.keys.single()
                active.size > 1 -> return null // Never end or duplicate two SIM calls from an ambiguous aggregate event.
                state == CallState.IDLE && sessions.filterValues { it.ended != null && now - it.endedAt in 0L..5_000L && (it.ended!!.alert.number.isBlank() || it.ended!!.alert.number == trimmedNumber) }.size == 1 ->
                    sessions.filterValues { it.ended != null && now - it.endedAt in 0L..5_000L && (it.ended!!.alert.number.isBlank() || it.ended!!.alert.number == trimmedNumber) }.keys.single()
                knownSubscriptions.size == 1 -> knownSubscriptions.single()
                else -> -1
            }
        }
        val session = sessions.getOrPut(key) { Session() }
        if (sim.isNotBlank()) session.sim = sim
        // A second IDLE/OFFHOOK broadcast can carry the number after the first ended the call.
        // Only enrich the most recent matching ended session; never create a call from it.
        if (session.current == null && state == CallState.IDLE && trimmedNumber.isNotEmpty()) {
            session.ended?.takeIf { now - session.endedAt in 0L..5_000L &&
                (it.alert.number.isBlank() || it.alert.number == trimmedNumber) }?.let {
                val updated = it.copy(alert = it.alert.copy(number = trimmedNumber))
                session.ended = updated
                snapshots[it.token] = updated
            }
        }
        val previousNumber = session.current?.alert?.number.orEmpty()
        if (trimmedNumber.isNotEmpty()) {
            session.current = session.current?.let { it.copy(alert = it.alert.copy(number = trimmedNumber)) }
        }
        // A delayed numbered RINGING broadcast must not undo an already-observed answer.
        if (fromBroadcast && state == CallState.RINGING && session.state == CallState.OFFHOOK &&
            now - session.offhookAt in 0L..5_000L && session.current != null &&
            (trimmedNumber.isBlank() || previousNumber.isBlank() || previousNumber == trimmedNumber)) {
            session.tracker.enrichNumber(trimmedNumber)
            session.current?.let { snapshots[it.token] = it }
            return CallEventResult(false, session.sim, null)
        }
        val changed = session.state != state
        if (state == CallState.OFFHOOK && changed) session.offhookAt = now
        session.state = state
        val generated = session.tracker.onState(state, trimmedNumber, now)
        if (generated?.kind == CallAlertKind.INCOMING) {
            session.ended = null
            session.current = RoutedCallAlert(++nextToken, generated, session.sim)
        } else if (state == CallState.RINGING) {
            session.current = session.current?.let {
                it.copy(alert = it.alert.copy(number = trimmedNumber.ifBlank { it.alert.number }), sim = session.sim)
            }
        }
        session.current?.let {
            snapshots[it.token] = it
            while (snapshots.size > 32) snapshots.remove(snapshots.keys.first())
        }
        val routed = when (generated?.kind) {
            CallAlertKind.INCOMING -> session.current
            CallAlertKind.MISSED -> session.current?.copy(alert = generated, sim = session.sim)
            null -> null
        }
        if (state == CallState.IDLE && session.current != null) {
            session.ended = session.current
            session.endedAt = now
            session.current = null
        }
        return CallEventResult(changed, session.sim, routed)
    }

    fun latest(token: Long): RoutedCallAlert? = snapshots[token]

    fun reset() {
        sessions.clear()
        snapshots.clear()
        knownSubscriptions = emptySet()
        // Tokens remain unique so an old delayed alert cannot refer to a later call.
    }
}
