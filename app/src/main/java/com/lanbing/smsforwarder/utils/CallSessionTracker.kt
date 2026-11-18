package com.lanbing.smsforwarder.utils

enum class CallState { IDLE, RINGING, OFFHOOK }
enum class CallAlertKind { INCOMING, MISSED }

data class CallAlert(
    val kind: CallAlertKind,
    val number: String,
    val startedAtMillis: Long
)

/** One tracker per SIM; repeated state callbacks must not send duplicate alerts. */
class CallSessionTracker {
    private var current: CallAlert? = null
    private var answered = false

    fun enrichNumber(number: String?) {
        val value = number?.trim().orEmpty()
        if (value.isNotEmpty()) current = current?.copy(number = value)
    }

    fun onState(state: CallState, number: String?, nowMillis: Long): CallAlert? {
        enrichNumber(number)
        val knownNumber = number?.trim().orEmpty()
        return when (state) {
            CallState.RINGING -> {
                if (current == null || answered) {
                    answered = false
                    CallAlert(CallAlertKind.INCOMING, knownNumber, nowMillis).also { current = it }
                } else {
                    if (knownNumber.isNotEmpty()) current = current!!.copy(number = knownNumber)
                    null
                }
            }
            CallState.OFFHOOK -> {
                if (current != null) answered = true
                null
            }
            CallState.IDLE -> {
                val missed = current?.takeUnless { answered }?.copy(kind = CallAlertKind.MISSED)
                reset()
                missed
            }
        }
    }

    fun reset() {
        current = null
        answered = false
    }
}

fun formatCallAlert(alert: CallAlert, time: String, device: String, sim: String, contactName: String? = null): String {
    val title = if (alert.kind == CallAlertKind.INCOMING) "来电提醒" else "未接／拒接提醒"
    return buildString {
        val name = ContactIdentity.cleanName(contactName)
        append("【$title】\n${if (name == null) "来电号码" else "来电人"}：${ContactIdentity.label(alert.number, name)}")
        append("\n来电时间：$time")
        if (sim.isNotBlank()) append("\n接收卡：$sim")
        if (device.isNotBlank()) append("\n设备：$device")
    }
}
