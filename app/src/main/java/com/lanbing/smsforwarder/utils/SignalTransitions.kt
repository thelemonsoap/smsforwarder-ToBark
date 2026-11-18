package com.lanbing.smsforwarder.utils

enum class MobileSignal(val label: String) { LTE("LTE"), FIVE_G("5G") }

/** Public Android RAT/override values; no framework dependency so routing can be unit tested. */
fun classifyMobileSignal(networkType: Int, overrideType: Int = 0): MobileSignal? = when {
    networkType == 20 -> MobileSignal.FIVE_G // NETWORK_TYPE_NR (SA)
    networkType == 13 && overrideType in setOf(3, 4, 5) -> MobileSignal.FIVE_G // NSA / advanced branding
    networkType == 13 -> MobileSignal.LTE // LTE / LTE CA / LTE advanced pro
    else -> null
}

data class SignalDecision(val signal: MobileSignal, val notify: Boolean)

/** Compare the end of a fixed confirmation window with the pre-window baseline.
 * Returning to the baseline does not cancel/restart the window: transient flapping is coalesced.
 */
class SignalTransitionTracker(
    lastStable: MobileSignal? = null,
    private var announceInitial: Boolean = true,
    private val windowMillis: Long = 300_000L
) {
    var stable: MobileSignal? = lastStable
        private set
    private var latest: MobileSignal? = null
    private var windowStarted: Long? = null

    fun observe(signal: MobileSignal?, now: Long) {
        latest = signal
        if (windowStarted == null && signal != null && signal != stable) windowStarted = now
    }

    /** Quiet observations become the baseline, never a deferred alert. */
    fun rebase(signal: MobileSignal?) {
        if (signal != null) stable = signal
        latest = signal
        windowStarted = null
        announceInitial = false
    }

    fun deadline(): Long? = windowStarted?.let { it + windowMillis }

    fun poll(now: Long): SignalDecision? {
        val deadline = deadline() ?: return null
        if (now < deadline) return null
        windowStarted = null
        // Unknown / unsupported networks or a return to the baseline produce no alert.
        val next = latest?.takeIf { it != stable } ?: return null
        val notify = stable != null || announceInitial
        stable = next
        return SignalDecision(next, notify)
    }
}

fun formatSignalAlert(signal: MobileSignal, sim: String = ""): String =
    "【信号提醒】现在使用${signal.label}" + if (sim.isBlank()) "" else "\n数据卡：$sim"
